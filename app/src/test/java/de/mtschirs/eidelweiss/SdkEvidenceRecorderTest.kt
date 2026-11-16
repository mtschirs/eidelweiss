package de.mtschirs.eidelweiss

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SdkEvidenceRecorderTest {
    @Test fun onlyKnownSdkMessageTypesAreRecorded() {
        var time = 1000L
        val log = SdkEvidenceRecorder(
            clockMs = { time },
            clockUtc = { "2026-11-16T00:00:00Z" },
            idProvider = { "laboratory-run-1" }
        )
        assertEquals("laboratory-run-1", log.begin())
        time += 10
        log.receivedSdkMessage("ACCESS_RIGHTS")
        log.receivedSdkMessage("PRIVATE_APDU_308390")
        log.receivedSdkMessage("AUTH")
        log.end()
        val run = log.snapshot().single()
        assertEquals(listOf(
            SdkEvidenceEvent.RUN_AUTH_REQUESTED,
            SdkEvidenceEvent.SDK_ACCESS_RIGHTS,
            SdkEvidenceEvent.SDK_AUTH_MESSAGE,
            SdkEvidenceEvent.RUN_ENDED
        ), run.marks.map { it.event })
        assertEquals(10L, run.marks[1].elapsedMs)
        assertEquals("laboratory-run-1", run.correlationId)
    }

    @Test fun reportsExplicitlyDenyChipLevelEvidence() {
        val log = SdkEvidenceRecorder(
            clockUtc = { "2026-11-16T00:00:00Z" },
            idProvider = { "test-id" }
        )
        log.begin()
        log.mark(SdkEvidenceEvent.USER_CONSENT_ACCEPTED, SdkEvidenceSource.UI_ACTION)
        log.end()
        val report = JSONObject(DiagnosticReport.toJson(
            emptyList(), emptyList(), sdkRuns = log.snapshot()
        ))
        assertEquals(4, report.getInt("schema_version"))
        val run = report.getJSONArray("sdk_observation_runs").getJSONObject(0)
        assertFalse(run.getBoolean("contains_apdu_trace"))
        assertFalse(run.getBoolean("proves_chip_session_continuity"))
        assertFalse(run.getBoolean("proves_terminal_authentication"))
        assertEquals("NOT_ESTABLISHED", report.getString("real_npa_security_conclusion"))
    }

    @Test fun activeRunIsExportableButClearlyNotFinished() {
        val log = SdkEvidenceRecorder(
            clockUtc = { "2026-11-16T00:00:00Z" },
            idProvider = { "in-progress" }
        )
        log.begin()
        val evidence = JSONObject(DiagnosticReport.toJson(
            emptyList(), emptyList(), sdkRuns = log.snapshot()
        ))
        assertTrue(evidence.getJSONArray("sdk_observation_runs").getJSONObject(0)
            .isNull("ended_at_utc"))
    }
}
