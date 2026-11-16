package de.mtschirs.eidelweiss

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SelfAuthProtocolTest {
    @Test fun productionCommandIsFixedAndNeverEnablesDeveloperMode() {
        val value = SelfAuthProtocol.startCommand()
        assertEquals("RUN_AUTH", value.getString("cmd"))
        assertEquals(SelfAuthProtocol.PRODUCTION_TC_TOKEN_URL, value.getString("tcTokenURL"))
        assertFalse(value.getBoolean("developerMode"))
        assertTrue(value.getBoolean("status"))
    }

    @Test fun testPkiCommandUsesOfficialTestServiceWithoutDeveloperMode() {
        val value = SelfAuthProtocol.startCommand(SelfAuthEnvironment.TEST_PKI)
        assertEquals("RUN_AUTH", value.getString("cmd"))
        assertEquals(SelfAuthProtocol.TEST_PKI_TC_TOKEN_URL, value.getString("tcTokenURL"))
        assertFalse(value.getBoolean("developerMode"))
        assertTrue(value.getBoolean("status"))
    }

    @Test fun testPkiModeIsPreservedInRedactedAuthSummary() {
        val message = JSONObject(
            """{"msg":"AUTH","result":{"major":"http://www.bsi.bund.de/ecard/api/1.1/resultmajor#ok"},"url":"https://example.org/?sessionId=PRIVATE"}"""
        )
        val result = SelfAuthProtocol.summarize(
            message, "2026-01-01T00:00:00Z", true, true, true,
            SelfAuthEnvironment.TEST_PKI
        )
        assertEquals("UNMODIFIED_AUSWEISAPP_SDK_TEST_PKI", result.mode)
        assertFalse(result.toString().contains("PRIVATE"))
    }

    @Test fun negotiatesHighestKnownApiLevel() {
        val message = JSONObject("""{"msg":"API_LEVEL","available":[1,2,3,99]}""")
        assertEquals(3, SelfAuthProtocol.apiLevelToSelect(message))
    }

    @Test fun summarizesFinalResultWithoutPersistingRefreshAddress() {
        val message = JSONObject(
            """{"msg":"AUTH","result":{"major":"http://www.bsi.bund.de/ecard/api/1.1/resultmajor#ok"},"url":"https://sample.invalid/?sessionId=SECRET"}"""
        )
        val report = SelfAuthProtocol.summarize(message, "2026-01-01T00:00:00Z", true, true, true)
        assertEquals("ok", report.outcome)
        assertFalse(report.toString().contains("SECRET"))
        assertEquals("https://sample.invalid/?sessionId=SECRET", SelfAuthProtocol.validatedResultUrl(message))
    }

    @Test fun doesNotExportArbitraryReasonOrHttpRedirect() {
        val message = JSONObject("""{"msg":"AUTH","result":{"major":"#error","reason":"Sensitive data 123"},"url":"http://example.com/"}""")
        assertEquals("error", SelfAuthProtocol.safeOutcome(message))
        assertNull(SelfAuthProtocol.safeReason(message))
        assertNull(SelfAuthProtocol.validatedResultUrl(message))
    }
}
