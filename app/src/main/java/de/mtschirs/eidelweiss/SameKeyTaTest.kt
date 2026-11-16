package de.mtschirs.eidelweiss

import org.json.JSONObject

/**
 * One precisely scoped test: a second valid TA with the same terminal public key,
 * following an EAC2 result error AFTER a successful first chip-side TA.
 *
 * This class does not execute APDUs or contact a server. Observations MUST originate
 * from an independently instrumented and authorized test-PKI laboratory.
 */
enum class SameKeyTaVerdict {
    REPORTED_SECOND_TA_ACCEPTED_REQUIRES_REVIEW,
    REPORTED_SECOND_TA_REJECTED_TESTED_CASE_ONLY,
    FIRST_TA_NOT_COMPLETED,
    INCONCLUSIVE,
    INVALID_EVIDENCE
}

data class SameKeyTaAssessment(
    val verdict: SameKeyTaVerdict,
    val reason: String,
    val runId: String?,
    val evidenceSource: String = "EXTERNAL_AUTHORIZED_LAB",
    val liveInjectionImplemented: Boolean = false,
    val rawApduVerifiedByApp: Boolean = false,
    val cryptographicProofVerifiedByApp: Boolean = false
)

object SameKeyTaTest {
    const val SCHEMA = "eidelweiss.same_key_ta.v1"
    const val FAULT_POINT = "EAC2_RESULT_ERROR_AFTER_ACCEPTED_FIRST_TA"
    const val TEST_ENVIRONMENT = "TEST_PKI"

    private val digest = Regex("[0-9a-fA-F]{64}")
    private val runIdRule = Regex("[A-Za-z0-9_-]{8,64}")
    private val allowedKeys = setOf(
        "schema", "run_id", "environment", "source", "fault_point",
        "first_ta_chip_result", "eac2_error_reported",
        "first_terminal_public_key_sha256", "second_terminal_public_key_sha256",
        "card_sm_session_continuous", "no_card_reset", "no_second_pace",
        "second_ta_fresh_signature_verified_by_lab", "second_ta_reached_chip",
        "second_ta_inner_status_verified", "second_ta_chip_result",
        "redacted_evidence_sha256"
    )

    fun evaluate(json: String): SameKeyTaAssessment {
        if (json.length > 65536) return invalid("Evidence document is too large")
        val objectValue = runCatching { JSONObject(json) }.getOrNull()
            ?: return invalid("Cannot parse JSON evidence object")
        val keys = objectValue.keys().asSequence().toSet()
        if (keys.any { it !in allowedKeys }) {
            return invalid("Unknown field; raw APDU, credentials and unrelated data are not accepted")
        }
        if (objectValue.optString("schema") != SCHEMA) return invalid("Incorrect evidence schema")
        val run = objectValue.optString("run_id").takeIf { runIdRule.matches(it) }
            ?: return invalid("Missing or invalid non-identifying run_id")
        fun result(verdict: SameKeyTaVerdict, reason: String): SameKeyTaAssessment =
            SameKeyTaAssessment(verdict, reason, run)
        if (objectValue.optString("environment") != TEST_ENVIRONMENT) {
            return result(SameKeyTaVerdict.INVALID_EVIDENCE, "Test-PKI only")
        }
        if (objectValue.optString("source") != "AUTHORIZED_LAB_CARD_AND_SERVER") {
            return result(SameKeyTaVerdict.INCONCLUSIVE,
                "SDK callbacks or offline simulations cannot substantiate TA execution")
        }
        if (objectValue.optString("fault_point") != FAULT_POINT) {
            return result(SameKeyTaVerdict.INVALID_EVIDENCE,
                "Only the post-first-TA EAC2 response error is in scope")
        }

        val first = objectValue.optString("first_ta_chip_result")
        if (first !in setOf("ACCEPTED", "REJECTED", "NOT_SENT", "UNKNOWN")) {
            return result(SameKeyTaVerdict.INVALID_EVIDENCE, "Unknown first TA chip result")
        }
        if (first != "ACCEPTED") {
            return result(SameKeyTaVerdict.FIRST_TA_NOT_COMPLETED,
                "Without an accepted first chip-side TA the next TA is not a repeated accepted TA")
        }
        if (objectValue.opt("eac2_error_reported") != true) {
            return result(SameKeyTaVerdict.INCONCLUSIVE,
                "No independently observed EAC2 result error after the first TA")
        }

        val firstKey = objectValue.optString("first_terminal_public_key_sha256")
        val secondKey = objectValue.optString("second_terminal_public_key_sha256")
        if (!digest.matches(firstKey) || !digest.matches(secondKey)) {
            return result(SameKeyTaVerdict.INCONCLUSIVE,
                "Both normalized terminal public-key SHA-256 values are required")
        }
        if (!firstKey.equals(secondKey, ignoreCase = true)) {
            return result(SameKeyTaVerdict.INVALID_EVIDENCE,
                "Terminal public keys differ; this is not the requested same-key test")
        }

        if (objectValue.opt("card_sm_session_continuous") != true ||
            objectValue.opt("no_card_reset") != true ||
            objectValue.opt("no_second_pace") != true) {
            return result(SameKeyTaVerdict.INCONCLUSIVE,
                "Continuity of the same chip-side SM session has not been substantiated")
        }
        if (objectValue.opt("second_ta_fresh_signature_verified_by_lab") != true ||
            objectValue.opt("second_ta_reached_chip") != true ||
            objectValue.opt("second_ta_inner_status_verified") != true) {
            return result(SameKeyTaVerdict.INCONCLUSIVE,
                "Missing independently checked fresh signature, chip delivery or authenticated inner status")
        }

        if (!digest.matches(objectValue.optString("redacted_evidence_sha256"))) {
            return result(SameKeyTaVerdict.INCONCLUSIVE,
                "A SHA-256 reference to the redacted independent trace is missing")
        }
        // A supplied hash proves no provenance: a human must inspect and sign off
        // on the independent card and server evidence, including verified DO'99.
        return when (objectValue.optString("second_ta_chip_result")) {
            "ACCEPTED" -> result(SameKeyTaVerdict.REPORTED_SECOND_TA_ACCEPTED_REQUIRES_REVIEW,
                "Reported same-key second TA acceptance; needs independent cryptographic review")
            "REJECTED" -> result(SameKeyTaVerdict.REPORTED_SECOND_TA_REJECTED_TESTED_CASE_ONLY,
                "Reported card-side TA rejection for this test card and error point only")
            "NOT_SENT", "UNKNOWN" -> result(SameKeyTaVerdict.INCONCLUSIVE,
                "Second TA result was not established at the chip")
            else -> result(SameKeyTaVerdict.INVALID_EVIDENCE, "Unknown second TA result")
        }
    }

    private fun invalid(reason: String) = SameKeyTaAssessment(
        SameKeyTaVerdict.INVALID_EVIDENCE, reason, null
    )
}
