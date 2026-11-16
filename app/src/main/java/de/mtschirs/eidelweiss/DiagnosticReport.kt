package de.mtschirs.eidelweiss

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Data-minimized reporting. Never include NFC UIDs, APDU bytes, certificate terms,
 * authentication refresh URLs, session IDs, PIN/CAN/PUK or personal card data.
 */
object DiagnosticReport {
    fun toJson(
        scenarios: List<ScenarioReport>,
        nfcObservations: List<String>,
        authRuns: List<AuthRunSummary> = emptyList()
    ): String {
        val cases = JSONArray()
        scenarios.forEach { scenario ->
            val transitions = JSONArray()
            scenario.transitions.forEach { step ->
                transitions.put(JSONObject()
                    .put("before", step.before.name)
                    .put("event", step.event.name)
                    .put("after", step.after.name)
                    .put("accepted", step.accepted)
                    .put("note", step.note))
            }
            cases.put(JSONObject()
                .put("name", scenario.name)
                .put("policy", scenario.policy.name)
                .put("anomaly_in_synthetic_model", scenario.anomaly)
                .put("transitions", transitions))
        }
        val auth = JSONArray()
        authRuns.forEach { run ->
            auth.put(JSONObject()
                .put("started_at_utc", run.startedAtUtc)
                .put("finished_at_utc", run.finishedAtUtc)
                .put("mode", run.mode)
                .put("outcome", run.outcome)
                .put("reason", run.reason ?: JSONObject.NULL)
                .put("certificate_shown", run.certificateShown)
                .put("access_rights_shown", run.accessRightsShown)
                .put("nfc_tag_forwarded", run.nfcTagForwarded))
        }
        return JSONObject()
            .put("schema_version", 2)
            .put("generated_at_utc", Instant.now().toString())
            .put("scope", "OFFLINE_SYNTHETIC_PASSIVE_NFC_AND_STANDARD_AUSWEISAPP_SDK_SELF_AUTH")
            .put("real_npa_security_conclusion", "NOT_ESTABLISHED")
            .put("nfc_observations", JSONArray(nfcObservations))
            .put("authentication_runs", auth)
            .put("cases", cases)
            .toString(2)
    }
}
