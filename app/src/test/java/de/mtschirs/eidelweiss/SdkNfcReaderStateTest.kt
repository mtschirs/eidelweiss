package de.mtschirs.eidelweiss

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SdkNfcReaderStateTest {
    @Test fun nfcReaderWithNoCardIsNotMistakenForTagAcceptance() {
        val message = JSONObject("""{"msg":"READER","name":"NFC","attached":true,"card":null}""")
        assertEquals(NfcReaderState.NO_CARD, SdkNfcReaderState.fromMessage(message))
    }

    @Test fun knownCardMustHaveDocumentedCardMetadata() {
        val message = JSONObject("""{"msg":"READER","name":"NFC","attached":true,"card":{"retryCounter":3}}""")
        assertEquals(NfcReaderState.CARD_RECOGNIZED, SdkNfcReaderState.fromMessage(message))
    }

    @Test fun unknownIsoDepCardIsDistinguished() {
        val message = JSONObject("""{"msg":"READER","name":"NFC","attached":true,"card":{}}""")
        assertEquals(NfcReaderState.UNKNOWN_CARD, SdkNfcReaderState.fromMessage(message))
    }

    @Test fun missingReaderOrHardwareIsVisible() {
        val list = JSONObject("""{"msg":"READER_LIST","readers":[{"name":"non-nfc serial reader","attached":true}]}""")
        assertEquals(NfcReaderState.NO_NFC_READER, SdkNfcReaderState.fromMessage(list))
        val detached = JSONObject("""{"msg":"READER","name":"NFC","attached":false,"card":null}""")
        assertEquals(NfcReaderState.READER_NOT_ATTACHED, SdkNfcReaderState.fromMessage(detached))
    }

    @Test fun otherReaderNamesDoNotLeakIntoAppEvidence() {
        val r = JSONObject("""{"msg":"READER","name":"USB Reader (SERIAL-SECRET)","card":{"retryCounter":3}}""")
        assertNull(SdkNfcReaderState.fromMessage(r))
    }

    @Test fun readerListWithCardIsRecognized() {
        val r = JSONObject("""{"msg":"READER_LIST","readers":[{"name":"NFC","attached":true,"card":{"inoperative":false}}]}""")
        assertEquals(NfcReaderState.CARD_RECOGNIZED, SdkNfcReaderState.fromMessage(r))
    }

    @Test fun statusSummaryDoesNotCopyUnrecognizedData() {
        val msg = JSONObject("""{"msg":"STATUS","progress":23,"state":"PRIVATE-SECRET","session":"must-never-appear"}""")
        val value = SdkNfcReaderState.safeStatusDescription(msg)
        assertEquals("Fortschritt 23%", value)
        assertFalse(value.contains("SECRET"))
    }
}
