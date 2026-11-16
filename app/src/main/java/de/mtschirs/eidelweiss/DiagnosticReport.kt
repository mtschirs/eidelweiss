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
        authRuns: List<AuthRunSummary> = emptyList(),
        labRuns: List<LabTrial> = emptyList(),
        sdkRuns: List<SdkEvidenceRun> = emptyList(),
        sameKeyAssessments: List<SameKeyTaAssessment> = emptyList()
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
        val lab = JSONArray()
        labRuns.forEach { trial ->
            val timeline = JSONArray()
            trial.steps.forEach { step ->
                timeline.put(JSONObject()
                    .put("step", step.index)
                    .put("server_transaction", step.serverTransaction)
                    .put("card_session", step.cardSession)
                    .put("operation", step.operation.name)
                    .put("result", step.result.name)
                    .put("secure_messaging", step.secureMessaging.name)
                    .put("successful_ta_count", step.successfulTaCount)
                    .put("note", step.note))
            }
            lab.put(JSONObject()
                .put("id", trial.id)
                .put("fault", trial.fault.name)
                .put("fault_stage", trial.fault.stage)
                .put("card_policy", trial.chipPolicy.name)
                .put("second_terminal", trial.nextTerminal.name)
                .put("first_ta_succeeded", trial.firstTaSucceeded)
                .put("first_ta_proof_attempted", trial.firstTaProofAttempted)
                .put("second_ta_accepted_in_model", trial.secondTaAccepted)
                .put("chip_session_preserved_in_model", trial.cardSessionPreserved)
                .put("execution_mode", "PURE_OFFLINE_SYNTHETIC")
                .put("real_card_evidence", false)
                .put("real_server_evidence", false)
                .put("steps", timeline))
        }
        val sdkEvidence = JSONArray()
        sdkRuns.forEach { run ->
            val marks = JSONArray()
            run.marks.forEach { entry ->
                marks.put(JSONObject()
                    .put("elapsed_ms", entry.elapsedMs)
                    .put("event", entry.event.name)
                    .put("source", entry.source.name))
            }
            sdkEvidence.put(JSONObject()
                .put("correlation_id", run.correlationId)
                .put("started_at_utc", run.startedUtc)
                .put("ended_at_utc", run.endedUtc ?: JSONObject.NULL)
                .put("source", "APP_LEVEL_SDK_CALLBACKS_AND_USER_ACTIONS_ONLY")
                .put("environment", run.environment)
                .put("contains_apdu_trace", false)
                .put("proves_chip_session_continuity", false)
                .put("proves_terminal_authentication", false)
                .put("marks", marks))
        }
        val sameKey = JSONArray()
        sameKeyAssessments.forEach { assessment ->
            sameKey.put(JSONObject()
                .put("test_case", SameKeyTaTest.FAULT_POINT)
                .put("environment", SameKeyTaTest.TEST_ENVIRONMENT)
                .put("run_id", assessment.runId ?: JSONObject.NULL)
                .put("verdict", assessment.verdict.name)
                .put("reason", assessment.reason)
                .put("evidence_source", assessment.evidenceSource)
                .put("live_injection_implemented", assessment.liveInjectionImplemented)
                .put("raw_apdu_verified_by_app", assessment.rawApduVerifiedByApp)
                .put("cryptographic_proof_verified_by_app", assessment.cryptographicProofVerifiedByApp))
        }
        return JSONObject()
            .put("schema_version", 5)
            .put("generated_at_utc", Instant.now().toString())
            .put("scope", if (BuildConfig.EIDELWEISS_LAB_AAR)
                "OFFLINE_SYNTHETIC_FAULT_LAB_PASSIVE_NFC_PUBLIC_CARDACCESS_PREFLIGHT_AND_INSTRUMENTED_AUSWEISAPP_SDK_SELF_AUTH"
                else "OFFLINE_SYNTHETIC_FAULT_LAB_PASSIVE_NFC_PUBLIC_CARDACCESS_PREFLIGHT_AND_STANDARD_AUSWEISAPP_SDK_SELF_AUTH")
            .put("sdk_engine", if (BuildConfig.EIDELWEISS_LAB_AAR)
                "LOCAL_INSTRUMENTED_CXX_AAR" else "OFFICIAL_UNMODIFIED_SDK_AAR")
            .put("real_npa_security_conclusion", "NOT_ESTABLISHED")
            .put("nfc_observations", JSONArray(nfcObservations))
            .put("authentication_runs", auth)
            .put("cases", cases)
            .put("eac2_fault_lab_trials", lab)
            .put("sdk_observation_runs", sdkEvidence)
            .put("same_key_ta_assessments", sameKey)
            .toString(2)
    }
}
