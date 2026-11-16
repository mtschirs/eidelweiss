package de.mtschirs.eidelweiss

import org.json.JSONObject

/**
 * Reduced NFC reader state parsed only from documented SDK READER / READER_LIST
 * messages. Card identifiers, the full reader name and PIN retry counters are
 * deliberately not copied to diagnostics.
 */
enum class NfcReaderState(val description: String, val evidenceEvent: SdkEvidenceEvent) {
    NO_NFC_READER("NFC-Leser nicht in der SDK-Liste", SdkEvidenceEvent.SDK_NFC_READER_MISSING),
    READER_NOT_ATTACHED("NFC-Leser laut SDK nicht verbunden", SdkEvidenceEvent.SDK_NFC_READER_DETACHED),
    NO_CARD("SDK meldet NFC-Leser, aber keinen Ausweis", SdkEvidenceEvent.SDK_NFC_READER_NO_CARD),
    UNKNOWN_CARD("SDK erkennt ein NFC-Objekt, aber keinen unterstützten Ausweis", SdkEvidenceEvent.SDK_NFC_READER_UNKNOWN_CARD),
    CARD_RECOGNIZED("SDK hat einen Ausweis am NFC-Leser erkannt", SdkEvidenceEvent.SDK_NFC_READER_CARD_RECOGNIZED)
}

object SdkNfcReaderState {
    fun fromMessage(message: JSONObject): NfcReaderState? {
        val reader = when (message.optString("msg")) {
            "READER" -> message.takeIf { it.optString("name") == "NFC" }
            "READER_LIST" -> {
                val list = message.optJSONArray("readers") ?: return null
                (0 until list.length()).asSequence()
                    .mapNotNull { list.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == "NFC" }
                    ?: return NfcReaderState.NO_NFC_READER
            }
            else -> return null
        } ?: return null

        if (!reader.optBoolean("attached", false)) return NfcReaderState.READER_NOT_ATTACHED
        val card = reader.optJSONObject("card") ?: return NfcReaderState.NO_CARD
        if (card.length() == 0) return NfcReaderState.UNKNOWN_CARD
        return if (card.has("retryCounter") || card.has("deactivated") ||
            card.has("inoperative")) NfcReaderState.CARD_RECOGNIZED
        else NfcReaderState.UNKNOWN_CARD
    }

    /**
     * SDK STATUS data is intentionally reduced to a percentage and one of a
     * small group of documented states. Never print entire JSON responses.
     */
    fun safeStatusDescription(message: JSONObject): String {
        val progress = message.optInt("progress", -1)
        val suffix = if (progress in 0..100) "Fortschritt $progress%" else "kein Fortschrittswert"
        val waiting = when (message.optString("state")) {
            "INSERT_CARD" -> ", wartet auf Ausweis"
            "ENTER_PIN" -> ", wartet auf PIN"
            "ENTER_CAN" -> ", wartet auf CAN"
            "PAUSE" -> ", pausiert"
            else -> ""
        }
        return "$suffix$waiting"
    }
}
