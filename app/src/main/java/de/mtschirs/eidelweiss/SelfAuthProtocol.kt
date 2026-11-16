package de.mtschirs.eidelweiss

import org.json.JSONObject
import java.time.Instant

/**
 * Normal, unmodified Governikus self-disclosure only. No configurable TA provider,
 * no EAC replay, no fault-injection or session substitution in this class.
 */
/**
 * The endpoints below are pinned to the unmodified selfAuthentication URL pair from
 * Governikus/AusweisApp resources/config.json.in (url and testUrl).
 * TEST_PKI changes the legitimate service, not the SDK's trust or TLS checks.
 */
enum class SelfAuthEnvironment(val displayName: String, val reportMode: String) {
    PRODUCTION("Produktiv (Echtausweis)", "UNMODIFIED_AUSWEISAPP_SDK_PRODUCTION"),
    TEST_PKI("Test-PKI (Testausweis)", "UNMODIFIED_AUSWEISAPP_SDK_TEST_PKI")
}

object SelfAuthProtocol {
    const val PRODUCTION_TC_TOKEN_URL =
        "https://www.autentapp.de/AusweisAuskunft/WebServiceRequesterServlet?mode=json"
    const val TEST_PKI_TC_TOKEN_URL =
        "https://test.governikus-eid.de/AusweisAuskunft/WebServiceRequesterServlet?mode=json"

    fun startCommand(
        environment: SelfAuthEnvironment = SelfAuthEnvironment.PRODUCTION
    ): JSONObject = JSONObject()
        .put("cmd", "RUN_AUTH")
        .put("tcTokenURL", if (environment == SelfAuthEnvironment.TEST_PKI)
            TEST_PKI_TC_TOKEN_URL else PRODUCTION_TC_TOKEN_URL)
        // Keep all certificate, TLS, and same-origin checks active also for test cards.
        .put("developerMode", false)
        .put("status", true)

    fun command(name: String): JSONObject = JSONObject().put("cmd", name)

    fun sdkMessageType(payload: JSONObject): String = payload.optString("msg")

    fun apiLevelToSelect(payload: JSONObject): Int? {
        if (payload.optString("msg") != "API_LEVEL") return null
        val available = payload.optJSONArray("available") ?: return null
        return (0 until available.length())
            .map { available.optInt(it, -1) }
            .filter { it in 1..3 }
            .maxOrNull()
    }

    fun isFinalAuth(payload: JSONObject): Boolean =
        payload.optString("msg") == "AUTH" && payload.optJSONObject("result") != null

    fun safeOutcome(payload: JSONObject): String {
        if (!isFinalAuth(payload)) return "incomplete"
        val major = payload.optJSONObject("result")?.optString("major").orEmpty()
        return if (major.endsWith("#ok")) "ok" else "error"
    }

    fun safeReason(payload: JSONObject): String? {
        val raw = payload.optJSONObject("result")?.optString("reason").orEmpty()
        // Keep only non-identifying documented failure identifiers.
        return raw.takeIf { it.length in 1..64 && it.matches(Regex("[A-Za-z0-9_.-]+")) }
    }

    fun validatedResultUrl(payload: JSONObject): String? {
        if (!isFinalAuth(payload)) return null
        // Never store this sensitive session-specific URL in exported reports.
        return payload.optString("url").takeIf { value ->
            value.length in 1..2048 && runCatching {
                val uri = java.net.URI(value)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }.getOrDefault(false)
        }
    }

    fun summarize(payload: JSONObject, startedAtUtc: String, certificateShown: Boolean,
                  rightsShown: Boolean, nfcForwarded: Boolean,
                  environment: SelfAuthEnvironment = SelfAuthEnvironment.PRODUCTION): AuthRunSummary =
        AuthRunSummary(
            startedAtUtc = startedAtUtc,
            finishedAtUtc = Instant.now().toString(),
            mode = environment.reportMode,
            outcome = safeOutcome(payload),
            reason = safeReason(payload),
            certificateShown = certificateShown,
            accessRightsShown = rightsShown,
            nfcTagForwarded = nfcForwarded
        )
}

data class AuthRunSummary(
    val startedAtUtc: String,
    val finishedAtUtc: String,
    val mode: String,
    val outcome: String,
    val reason: String?,
    val certificateShown: Boolean,
    val accessRightsShown: Boolean,
    val nfcTagForwarded: Boolean
)
