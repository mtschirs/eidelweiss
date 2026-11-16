package de.mtschirs.eidelweiss

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SameKeyTaTestTest {
    private val key = "2".repeat(64)
    private val other = "3".repeat(64)

    private fun evidence(
        first: String = "ACCEPTED",
        second: String = "REJECTED"
    ): JSONObject = JSONObject()
        .put("schema", SameKeyTaTest.SCHEMA)
        .put("run_id", "test_2026_0001")
        .put("environment", "TEST_PKI")
        .put("source", "AUTHORIZED_LAB_CARD_AND_SERVER")
        .put("fault_point", SameKeyTaTest.FAULT_POINT)
        .put("first_ta_chip_result", first)
        .put("eac2_error_reported", true)
        .put("first_terminal_public_key_sha256", key)
        .put("second_terminal_public_key_sha256", key)
        .put("card_sm_session_continuous", true)
        .put("no_card_reset", true)
        .put("no_second_pace", true)
        .put("second_ta_fresh_signature_verified_by_lab", true)
        .put("second_ta_reached_chip", true)
        .put("second_ta_inner_status_verified", true)
        .put("second_ta_chip_result", second)
        .put("redacted_evidence_sha256", "4".repeat(64))

    @Test fun reportedRejectionIsLimitedToTheTestedChip() {
        val result = SameKeyTaTest.evaluate(evidence().toString())
        assertEquals(SameKeyTaVerdict.REPORTED_SECOND_TA_REJECTED_TESTED_CASE_ONLY, result.verdict)
        assertFalse(result.cryptographicProofVerifiedByApp)
        assertFalse(result.liveInjectionImplemented)
    }

    @Test fun reportedAcceptanceIsNotMisrepresentedAsCryptographicProof() {
        val result = SameKeyTaTest.evaluate(evidence(second = "ACCEPTED").toString())
        assertEquals(SameKeyTaVerdict.REPORTED_SECOND_TA_ACCEPTED_REQUIRES_REVIEW, result.verdict)
        assertFalse(result.rawApduVerifiedByApp)
    }

    @Test fun missingFirstTaIsNotATaRepeat() {
        val result = SameKeyTaTest.evaluate(evidence(first = "NOT_SENT").toString())
        assertEquals(SameKeyTaVerdict.FIRST_TA_NOT_COMPLETED, result.verdict)
    }

    @Test fun keyMismatchInvalidatesTheSameKeyClaim() {
        val input = evidence().put("second_terminal_public_key_sha256", other)
        val result = SameKeyTaTest.evaluate(input.toString())
        assertEquals(SameKeyTaVerdict.INVALID_EVIDENCE, result.verdict)
    }

    @Test fun changedSessionIsNotEvidenceOfRepeat() {
        val input = evidence().put("no_second_pace", false)
        assertEquals(SameKeyTaVerdict.INCONCLUSIVE,
            SameKeyTaTest.evaluate(input.toString()).verdict)
    }

    @Test fun missingIndependentInnerStatusIsInconclusive() {
        val input = evidence().put("second_ta_inner_status_verified", false)
        assertEquals(SameKeyTaVerdict.INCONCLUSIVE,
            SameKeyTaTest.evaluate(input.toString()).verdict)
    }

    @Test fun missingFreshSignatureIsInconclusive() {
        val input = evidence().put("second_ta_fresh_signature_verified_by_lab", false)
        assertEquals(SameKeyTaVerdict.INCONCLUSIVE,
            SameKeyTaTest.evaluate(input.toString()).verdict)
    }

    @Test fun unverifiedSdkOnlyEvidenceCannotClaimTA() {
        val input = evidence().put("source", "UNMODIFIED_AUSWEISAPP_SDK")
        assertEquals(SameKeyTaVerdict.INCONCLUSIVE,
            SameKeyTaTest.evaluate(input.toString()).verdict)
    }

    @Test fun onlyThisFaultPointIsAllowed() {
        val input = evidence().put("fault_point", "BEFORE_FIRST_TA")
        assertEquals(SameKeyTaVerdict.INVALID_EVIDENCE,
            SameKeyTaTest.evaluate(input.toString()).verdict)
    }

    @Test fun rejectUnexpectedInputFieldsIncludingRawApdu() {
        val input = evidence().put("raw_apdu", "00A40400")
        assertEquals(SameKeyTaVerdict.INVALID_EVIDENCE,
            SameKeyTaTest.evaluate(input.toString()).verdict)
    }

    @Test fun incorrectEnvironmentNeverPasses() {
        val input = evidence().put("environment", "PRODUCTION")
        assertEquals(SameKeyTaVerdict.INVALID_EVIDENCE,
            SameKeyTaTest.evaluate(input.toString()).verdict)
    }

    @Test fun malformedOrTooLongJsonFailsClosed() {
        assertEquals(SameKeyTaVerdict.INVALID_EVIDENCE,
            SameKeyTaTest.evaluate("broken").verdict)
        assertEquals(SameKeyTaVerdict.INVALID_EVIDENCE,
            SameKeyTaTest.evaluate("x".repeat(65537)).verdict)
    }

    @Test fun diagnosticExportCannotPretendToValidateAPDUOrCrypto() {
        val value = SameKeyTaTest.evaluate(evidence(second = "ACCEPTED").toString())
        val report = JSONObject(
            DiagnosticReport.toJson(emptyList(), emptyList(), sameKeyAssessments = listOf(value))
        )
        assertEquals(5, report.getInt("schema_version"))
        val entry = report.getJSONArray("same_key_ta_assessments").getJSONObject(0)
        assertEquals(SameKeyTaVerdict.REPORTED_SECOND_TA_ACCEPTED_REQUIRES_REVIEW.name,
            entry.getString("verdict"))
        assertFalse(entry.getBoolean("live_injection_implemented"))
        assertFalse(entry.getBoolean("raw_apdu_verified_by_app"))
        assertFalse(entry.getBoolean("cryptographic_proof_verified_by_app"))
        assertEquals("NOT_ESTABLISHED", report.getString("real_npa_security_conclusion"))
    }

}
