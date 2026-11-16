#!/usr/bin/env python3
"""
Summarize EIDELWEISS_EAC2_LAB logcat lines from a physical test-card run.

This tool recognizes only fixed instrumentation markers; it does not parse,
store, decrypt or transmit APDU data or prove a second Terminal Authentication.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

MARKER = "EIDELWEISS_EAC2_LAB"
EVENT_RE = re.compile(r"(?:^|\s)phase=([a-z0-9_]+)")
KEYS = frozenset({
    "ta_first", "post_ta", "eac2_command", "eac2_paos_response",
    "post_eac2_server_message", "post_eac2_transmit_received",
    "transmit_to_card", "transmit_response", "pace_secure_messaging_start",
    "secure_messaging_stop", "destroy_pace_requested",
})
ALLOWED_LINES = 5000


def summarize(lines: list[str]) -> dict:
    events: list[str] = []
    for line in lines[:ALLOWED_LINES]:
        if MARKER not in line:
            continue
        match = EVENT_RE.search(line)
        if match and match.group(1) in KEYS:
            phase = match.group(1)
            if phase == "ta_first":
                if "result=accepted_by_chip" in line:
                    events.append("FIRST_TA_ACCEPTED")
                elif "result=rejected_by_chip_or_transport" in line:
                    events.append("FIRST_TA_NOT_ACCEPTED")
                continue
            if phase == "post_ta" and "fault_injected_before_ca" in line:
                events.append("POST_TA_FAULT_INJECTED")
                continue
            if phase == "post_eac2_transmit_received":
                events.append("PAOS_TRANSMIT_RECEIVED")
                continue
            events.append(phase.upper())
    ta_ok = "FIRST_TA_ACCEPTED" in events
    injected = "POST_TA_FAULT_INJECTED" in events
    transmit = "PAOS_TRANSMIT_RECEIVED" in events
    stopped = "SECURE_MESSAGING_STOP" in events
    pace_reset = "PACE_SECURE_MESSAGING_START" in events
    return {
        "schema": "eidelweiss.native_eac2_trace.v1",
        "source": "INSTRUMENTED_NATIVE_ANDROID_LOGCAT",
        "physical_card_claim_requires_independent_review": True,
        "first_ta_reported_accepted": ta_ok,
        "post_ta_eac2_error_reported_injected": injected,
        "post_eac2_transmit_reported_received": transmit,
        "secure_messaging_stop_seen": stopped,
        "pace_start_seen_during_capture": pace_reset,
        "second_fresh_ta_signature_proven": False,
        "second_ta_chip_acceptance_proven": False,
        "same_card_session_continuity_proven": False,
        "experimental_conclusion": (
            "NATIVE_FIRST_TA_AND_INJECTED_EAC2_ERROR_REPORTED"
            if ta_ok and injected else "INCONCLUSIVE"
        ),
        "recognized_events": events,
        "limitations": (
            "The filtered log alone cannot prove cryptographically valid second TA, "
            "absence of an RF reset, or Secure Messaging continuity."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logcat", type=Path, help="Only lines matching EIDELWEISS_EAC2_LAB")
    args = parser.parse_args()
    if args.logcat.stat().st_size > 2_000_000:
        parser.error("Input exceeds 2 MB")
    data = args.logcat.read_text(encoding="utf-8", errors="replace").splitlines()
    print(json.dumps(summarize(data), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
