package de.mtschirs.eidelweiss

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Data-minimized reporting. Never include tag identifiers, APDU data, or personal data. */
object DiagnosticReport {
    fun toJson(scenarios: List<ScenarioReport>, nfcObservations: List<String>): String {
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
        return JSONObject()
            .put("schema_version", 1)
            .put("generated_at_utc", Instant.now().toString())
            .put("scope", "OFFLINE_SYNTHETIC_AND_NFC_DISCOVERY_ONLY")
            .put("real_npa_security_conclusion", "NOT_ESTABLISHED")
            .put("nfc_observations", JSONArray(nfcObservations))
            .put("cases", cases)
            .toString(2)
    }
}
