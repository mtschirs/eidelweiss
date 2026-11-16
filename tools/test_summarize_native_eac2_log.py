import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parent))
from summarize_native_eac2_log import summarize


class NativeEac2TraceTest(unittest.TestCase):
    def test_injected_fault_after_real_first_ta_is_summarized(self):
        trace = [
            "I/AusweisApp EIDELWEISS_EAC2_LAB phase=pace_secure_messaging_start",
            "I/AusweisApp EIDELWEISS_EAC2_LAB phase=ta_first result=accepted_by_chip",
            "W/AusweisApp EIDELWEISS_EAC2_LAB phase=post_ta result=fault_injected_before_ca",
            "I/AusweisApp EIDELWEISS_EAC2_LAB phase=eac2_paos_response error=true",
            "I/AusweisApp EIDELWEISS_EAC2_LAB phase=post_eac2_transmit_received",
        ]
        result = summarize(trace)
        self.assertTrue(result["first_ta_reported_accepted"])
        self.assertTrue(result["post_ta_eac2_error_reported_injected"])
        self.assertTrue(result["post_eac2_transmit_reported_received"])
        self.assertFalse(result["second_ta_chip_acceptance_proven"])
        self.assertFalse(result["same_card_session_continuity_proven"])

    def test_rejected_first_ta_is_not_reported_as_success(self):
        trace = ["EIDELWEISS_EAC2_LAB phase=ta_first result=rejected_by_chip_or_transport"]
        result = summarize(trace)
        self.assertFalse(result["first_ta_reported_accepted"])
        self.assertEqual("INCONCLUSIVE", result["experimental_conclusion"])

    def test_untrusted_payloads_are_not_copied(self):
        trace = [
            "EIDELWEISS_EAC2_LAB phase=ta_first result=accepted_by_chip PIN=SECRET SESSION=ID",
            "EIDELWEISS_EAC2_LAB phase=post_ta result=fault_injected_before_ca",
        ]
        result = summarize(trace)
        self.assertNotIn("SECRET", str(result))
        self.assertNotIn("SESSION=ID", str(result))
        self.assertTrue(result["first_ta_reported_accepted"])

    def test_sm_reset_makes_no_false_session_continuity_claim(self):
        trace = [
            "EIDELWEISS_EAC2_LAB phase=ta_first result=accepted_by_chip",
            "EIDELWEISS_EAC2_LAB phase=secure_messaging_stop",
        ]
        result = summarize(trace)
        self.assertTrue(result["secure_messaging_stop_seen"])
        self.assertFalse(result["same_card_session_continuity_proven"])


if __name__ == "__main__":
    unittest.main()
