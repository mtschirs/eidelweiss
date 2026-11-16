package de.mtschirs.eidelweiss

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun eac2LabIsExplicitlyMarkedOfflineAndNotRealEvidence() {
        val run = Eac2FaultLab.run(LabFaultPoint.CHAIN_MISSING)
        val json = JSONObject(
            DiagnosticReport.toJson(
                scenarios = emptyList(),
                nfcObservations = emptyList(),
                labRuns = listOf(run)
            )
        )
        assertEquals(5, json.getInt("schema_version"))
        assertEquals("NOT_ESTABLISHED", json.getString("real_npa_security_conclusion"))
        val lab = json.getJSONArray("eac2_fault_lab_trials").getJSONObject(0)
        assertEquals("PURE_OFFLINE_SYNTHETIC", lab.getString("execution_mode"))
        assertFalse(lab.getBoolean("real_card_evidence"))
        assertFalse(lab.getBoolean("real_server_evidence"))
        assertEquals(2, lab.getJSONArray("steps")
            .getJSONObject(lab.getJSONArray("steps").length() - 1).getInt("server_transaction"))
        assertEquals(1, lab.getJSONArray("steps")
            .getJSONObject(lab.getJSONArray("steps").length() - 1).getInt("card_session"))
    }

    @Test fun reportDoesNotIntroduceSessionIdentifiersOrSensitivePayloads() {
        val json = DiagnosticReport.toJson(
            scenarios = emptyList(),
            nfcObservations = emptyList(),
            labRuns = Eac2FaultLab.runMatrix()
        )
        assertFalse(json.contains("tcTokenURL"))
        assertFalse(json.contains("sessionId"))
        assertFalse(json.contains("PK_PCD"))
        assertFalse(json.contains("PIN_VALUE"))
        assertFalse(json.contains("APDU_PAYLOAD"))
    }

    @Test fun regularSdkOutcomeAndOfflineLaboratoryRemainDistinct() {
        val auth = AuthRunSummary(
            startedAtUtc = "2026-01-01T00:00:00Z",
            finishedAtUtc = "2026-01-01T00:01:00Z",
            mode = "UNMODIFIED_AUSWEISAPP_SDK",
            outcome = "ok",
            reason = null,
            certificateShown = true,
            accessRightsShown = true,
            nfcTagForwarded = true
        )
        val json = JSONObject(
            DiagnosticReport.toJson(
                scenarios = emptyList(),
                nfcObservations = emptyList(),
                authRuns = listOf(auth),
                labRuns = listOf(Eac2FaultLab.run(LabFaultPoint.NONE))
            )
        )
        assertEquals("ok", json.getJSONArray("authentication_runs")
            .getJSONObject(0).getString("outcome"))
        assertFalse(json.getJSONArray("eac2_fault_lab_trials")
            .getJSONObject(0).getBoolean("real_card_evidence"))
    }
}
