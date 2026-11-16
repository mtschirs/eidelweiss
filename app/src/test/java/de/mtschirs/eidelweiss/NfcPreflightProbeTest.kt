package de.mtschirs.eidelweiss

import org.junit.Assert.*
import org.junit.Test

class NfcPreflightProbeTest {
    @Test fun selectUsesDocumentedCardAccessFileId() {
        assertArrayEquals(
            byteArrayOf(0, 0xA4.toByte(), 2, 0x0C, 2, 1, 0x1C),
            CardAccessApdu.select()
        )
        assertArrayEquals(
            byteArrayOf(0, 0xB0.toByte(), 0, 0, 1),
            CardAccessApdu.readOneByte()
        )
    }

    @Test fun readingPublicEfCardAccessConfirmsBasicIsoDepCommandPath() {
        val result = CardAccessApdu.classify(
            byteArrayOf(0x90.toByte(), 0x00),
            byteArrayOf(0x31, 0x90.toByte(), 0x00)
        )
        assertEquals(NfcPreflightOutcome.CARD_ACCESS_OK, result.outcome)
        assertEquals("9000", result.selectStatus)
        assertEquals("9000", result.readStatus)
    }

    @Test fun selectionFailureDoesNotClaimCardRecognized() {
        val result = CardAccessApdu.classify(
            byteArrayOf(0x6A, 0x82.toByte()), null
        )
        assertEquals(NfcPreflightOutcome.CARD_ACCESS_SELECT_REJECTED, result.outcome)
        assertEquals("6A82", result.selectStatus)
    }

    @Test fun readFailureIsDistinguishedFromSelectFailure() {
        val result = CardAccessApdu.classify(
            byteArrayOf(0x90.toByte(), 0),
            byteArrayOf(0x69, 0x82.toByte())
        )
        assertEquals(NfcPreflightOutcome.CARD_ACCESS_READ_REJECTED, result.outcome)
        assertEquals("6982", result.readStatus)
    }

    @Test fun truncatedOrNonDerResponsesAreNotAccepted() {
        assertEquals(NfcPreflightOutcome.CARD_ACCESS_RESPONSE_MALFORMED,
            CardAccessApdu.classify(byteArrayOf(0x90.toByte()), null).outcome)
        assertEquals(NfcPreflightOutcome.CARD_ACCESS_RESPONSE_MALFORMED,
            CardAccessApdu.classify(
                byteArrayOf(0x90.toByte(), 0),
                byteArrayOf(0x30, 0x90.toByte(), 0)
            ).outcome)
    }

    @Test fun summaryExposesOnlyStatusesAndLinkCapabilities() {
        val result = NfcPreflightResult(
            NfcPreflightOutcome.CARD_ACCESS_OK, true, 510, "9000", "9000"
        )
        val summary = result.safeSummary()
        assertTrue(summary.contains("CARD_ACCESS_OK"))
        assertTrue(summary.contains("maxTransceive=510"))
        assertFalse(summary.contains("31 90 00"))
        assertFalse(summary.contains("UID"))
    }
}
