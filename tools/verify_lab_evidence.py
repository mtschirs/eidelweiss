#!/usr/bin/env python3
"""
Inspect an authorized-laboratory evidence manifest.

This is an *offline integrity/consistency checker*, not a crypto verifier, card
simulator, APDU injector, or exploit tool.  It never sends network requests.
Every reported classification is explicitly subject to independent review.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

VERSION = 1
REQUIRED_ROLES = {
    "card_apdu_trace_redacted",
    "server_eac_trace_redacted",
    "client_paos_trace_redacted",
    "consent_record_redacted",
}
KNOWN_ROLES = REQUIRED_ROLES | {"reader_rf_trace_redacted", "chip_metadata_redacted"}
OBSERVATION_KEYS = {
    "baseline_completed",
    "client_forwarded_after_eac2_error",
    "single_chip_sm_session_confirmed",
    "valid_second_ta_signature_confirmed",
    "second_ta_reached_chip",
    "different_terminal_identity_confirmed",
    "user_approved_da1_only",
    "server_authentication_as_da2_confirmed",
}


def _secure_file(path_value: str, root: Path) -> Path:
    if not isinstance(path_value, str) or not path_value or len(path_value) > 240:
        raise ValueError("Artifact path must be a short relative path")
    relative = Path(path_value)
    if relative.is_absolute() or any(part in ("..", "") for part in relative.parts):
        raise ValueError("Absolute and traversing artifact paths are rejected")
    path = (root / relative).resolve(strict=True)
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        raise ValueError("Artifact path escapes evidence directory or is not a file")
    if path.stat().st_size > 20 * 1024 * 1024:
        raise ValueError("Artifact exceeds 20 MiB limit")
    return path


def inspect_evidence(manifest: dict, root: Path) -> dict:
    errors: list[str] = []
    verified_roles: set[str] = set()
    if not isinstance(manifest, dict) or manifest.get("schema_version") != VERSION:
        return {
            "classification": "INVALID",
            "errors": ["Expected lab manifest schema_version=1"],
            "independent_review_required": True,
        }

    run_id = manifest.get("run_id", "")
    if not isinstance(run_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{8,64}", run_id):
        errors.append("Invalid/non-redacted run_id; use a random correlation identifier")

    observations = manifest.get("observations")
    if not isinstance(observations, dict):
        errors.append("observations must be an object")
        observations = {}
    if set(observations) - (OBSERVATION_KEYS | {"second_ta_card_response"}):
        errors.append("Unexpected observation keys")
    for key in OBSERVATION_KEYS:
        if observations.get(key) not in (True, False, None) or (
            key in observations and type(observations[key]) not in (bool, type(None))
        ):
            errors.append("Observation " + key + " must be true, false or null")
    result = observations.get("second_ta_card_response")
    if result not in ("accepted", "rejected", "not_observed"):
        errors.append("second_ta_card_response must be accepted/rejected/not_observed")

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) > 16:
        errors.append("artifacts must be a list with at most 16 entries")
        artifacts = []
    for idx, item in enumerate(artifacts):
        if not isinstance(item, dict):
            errors.append(f"artifact[{idx}] must be an object")
            continue
        role = item.get("role")
        digest = item.get("sha256")
        if role not in KNOWN_ROLES:
            errors.append(f"artifact[{idx}] has an unsupported evidence role")
            continue
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
            errors.append(f"artifact[{idx}] has invalid sha256")
            continue
        try:
            path = _secure_file(item.get("file"), root)
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                errors.append(f"artifact[{idx}] digest mismatch")
            else:
                verified_roles.add(role)
        except (OSError, ValueError, TypeError) as exc:
            errors.append(f"artifact[{idx}] invalid: {type(exc).__name__}")

    missing = sorted(REQUIRED_ROLES - verified_roles)
    authorized = manifest.get("authorization_scope") == "approved_lab"
    if not authorized:
        # Recording an unapproved trial must not be interpreted as permission to run it.
        missing.append("approved_lab_authorization")
    if errors:
        classification = "INVALID"
    elif not authorized or missing:
        classification = "INCONCLUSIVE"
    elif (
        observations.get("baseline_completed") is True
        and observations.get("client_forwarded_after_eac2_error") is True
        and observations.get("single_chip_sm_session_confirmed") is True
        and observations.get("valid_second_ta_signature_confirmed") is True
        and observations.get("second_ta_reached_chip") is True
        and result == "accepted"
        and observations.get("different_terminal_identity_confirmed") is True
        and observations.get("user_approved_da1_only") is True
        and observations.get("server_authentication_as_da2_confirmed") is True
    ):
        classification = "FULL_CHAIN_REPORTED_REQUIRES_REVIEW"
    elif (
        observations.get("baseline_completed") is True
        and observations.get("single_chip_sm_session_confirmed") is True
        and observations.get("valid_second_ta_signature_confirmed") is True
        and observations.get("second_ta_reached_chip") is True
        and result == "rejected"
    ):
        classification = "CARD_REJECTION_REPORTED_IN_TESTED_CONFIGURATION"
    elif observations.get("client_forwarded_after_eac2_error") is True:
        classification = "CLIENT_CONTINUATION_REPORTED_ONLY"
    else:
        classification = "INCONCLUSIVE"

    return {
        "run_id": run_id,
        "classification": classification,
        "checked_artifact_roles": sorted(verified_roles),
        "missing_evidence_roles_or_scope": sorted(set(missing)),
        "errors": errors,
        "independent_review_required": True,
        "limits": (
            "File SHA-256 verifies equality to the supplied manifest, not provenance, "
            "TA signature validity, session continuity, or security of the chip. "
            "No single negative trial proves universal safety."
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Path to JSON evidence manifest")
    args = parser.parse_args(argv)
    if args.manifest.stat().st_size > 1024 * 1024:
        parser.error("Manifest is larger than 1 MiB")
    try:
        data = json.loads(args.manifest.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        parser.error("Could not load evidence manifest: " + str(exc))
    assessment = inspect_evidence(data, args.manifest.parent)
    print(json.dumps(assessment, ensure_ascii=False, indent=2, sort_keys=True))
    return 2 if assessment["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
