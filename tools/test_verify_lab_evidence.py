import hashlib
import json
import tempfile
from pathlib import Path
import unittest

from verify_lab_evidence import inspect_evidence

ROLES = (
    "card_apdu_trace_redacted",
    "server_eac_trace_redacted",
    "client_paos_trace_redacted",
    "consent_record_redacted",
)

def evidence(allowed=True):
    return {
        "schema_version": 1,
        "run_id": "test_run_2026",
        "authorization_scope": "approved_lab" if allowed else "pending",
        "observations": {
            "baseline_completed": True,
            "client_forwarded_after_eac2_error": True,
            "single_chip_sm_session_confirmed": True,
            "valid_second_ta_signature_confirmed": True,
            "second_ta_reached_chip": True,
            "second_ta_card_response": "accepted",
            "different_terminal_identity_confirmed": True,
            "user_approved_da1_only": True,
            "server_authentication_as_da2_confirmed": True,
        },
        "artifacts": [],
    }

def add_artifacts(manifest, root):
    for idx, role in enumerate(ROLES):
        raw = ("redacted test fixture " + str(idx)).encode()
        name = role + ".txt"
        (root / name).write_bytes(raw)
        manifest["artifacts"].append({
            "role": role,
            "file": name,
            "sha256": hashlib.sha256(raw).hexdigest(),
        })


class EvidenceVerifierTests(unittest.TestCase):
    def test_complete_claim_is_only_reported_not_cryptographically_verified(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = evidence()
            add_artifacts(manifest, root)
            result = inspect_evidence(manifest, root)
            self.assertEqual("FULL_CHAIN_REPORTED_REQUIRES_REVIEW", result["classification"])
            self.assertTrue(result["independent_review_required"])
            self.assertEqual([], result["errors"])

    def test_accepted_second_ta_without_da2_proof_is_inconclusive(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = evidence()
            add_artifacts(manifest, root)
            manifest["observations"]["server_authentication_as_da2_confirmed"] = None
            self.assertEqual("CLIENT_CONTINUATION_REPORTED_ONLY",
                inspect_evidence(manifest, root)["classification"])

    def test_specific_card_rejection_is_not_global_disproof(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = evidence()
            add_artifacts(manifest, root)
            manifest["observations"]["second_ta_card_response"] = "rejected"
            manifest["observations"]["server_authentication_as_da2_confirmed"] = False
            self.assertEqual("CARD_REJECTION_REPORTED_IN_TESTED_CONFIGURATION",
                inspect_evidence(manifest, root)["classification"])

    def test_tampered_artifact_is_invalid(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = evidence()
            add_artifacts(manifest, root)
            (root / (ROLES[0] + ".txt")).write_text("TAMPERED")
            self.assertEqual("INVALID", inspect_evidence(manifest, root)["classification"])

    def test_unapproved_or_missing_artifacts_stays_inconclusive(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = evidence(False)
            self.assertEqual("INCONCLUSIVE",
                inspect_evidence(manifest, Path(tmp))["classification"])

    def test_path_traversal_cannot_be_hashed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = evidence()
            manifest["artifacts"].append({
                "role": ROLES[0],
                "file": "../outside",
                "sha256": "0" * 64,
            })
            self.assertEqual("INVALID", inspect_evidence(manifest, root)["classification"])

    def test_unknown_fields_cannot_be_misclassified(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = evidence()
            add_artifacts(manifest, root)
            manifest["observations"]["secret_payload"] = "sensitive"
            self.assertEqual("INVALID", inspect_evidence(manifest, root)["classification"])


if __name__ == "__main__":
    unittest.main()
