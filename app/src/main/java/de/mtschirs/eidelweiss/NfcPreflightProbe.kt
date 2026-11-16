package de.mtschirs.eidelweiss

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import java.io.IOException

/**
 * Public, pre-PACE NFC preflight. This is ONLY called when no AusweisApp
 * authentication is in progress, and never uses the SDK's live NFC target.
 *
 * Reads one byte of the always-public EF.CardAccess to discriminate RF /
 * ISO-DEP transport from AusweisApp SDK card-recognition failures.
 * No data bytes or card identifiers are returned, retained or logged.
 */
enum class NfcPreflightOutcome {
    NON_ISODEP,
    CARD_ACCESS_OK,
    CARD_ACCESS_SELECT_REJECTED,
    CARD_ACCESS_READ_REJECTED,
    CARD_ACCESS_RESPONSE_MALFORMED,
    CONNECTION_LOST,
    IO_ERROR,
    SECURITY_ERROR
}

data class NfcPreflightResult(
    val outcome: NfcPreflightOutcome,
    val extendedApdu: Boolean? = null,
    val maxTransceiveLength: Int? = null,
    val selectStatus: String? = null,
    val readStatus: String? = null
) {
    fun safeSummary(): String = buildString {
        append("NFC-Preflight: ")
        append(outcome.name)
        selectStatus?.let { append("; SELECT_SW=").append(it) }
        readStatus?.let { append("; READ_SW=").append(it) }
        extendedApdu?.let { append("; extended=").append(it) }
        maxTransceiveLength?.let { append("; maxTransceive=").append(it) }
        append("; keine Ausweisdaten/PIN gelesen")
    }
}

/** Public ISO-7816 commands for MF/EF.CardAccess (FID 011C). */
object CardAccessApdu {
    fun select(): ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x02, 0x0C, 0x02, 0x01, 0x1C
    )

    fun readOneByte(): ByteArray = byteArrayOf(
        0x00, 0xB0.toByte(), 0x00, 0x00, 0x01
    )

    fun statusWord(response: ByteArray): String? {
        if (response.size < 2) return null
        return "%02X%02X".format(
            response[response.size - 2].toInt() and 0xFF,
            response[response.size - 1].toInt() and 0xFF
        )
    }

    fun classify(
        selectResponse: ByteArray,
        readResponse: ByteArray?
    ): NfcPreflightResult {
        val selectStatus = statusWord(selectResponse)
        if (selectStatus == null) return NfcPreflightResult(
            NfcPreflightOutcome.CARD_ACCESS_RESPONSE_MALFORMED
        )
        if (selectStatus != "9000") return NfcPreflightResult(
            NfcPreflightOutcome.CARD_ACCESS_SELECT_REJECTED, selectStatus = selectStatus
        )
        val read = readResponse ?: return NfcPreflightResult(
            NfcPreflightOutcome.CARD_ACCESS_RESPONSE_MALFORMED,
            selectStatus = selectStatus
        )
        val readStatus = statusWord(read)
        if (readStatus == null) return NfcPreflightResult(
            NfcPreflightOutcome.CARD_ACCESS_RESPONSE_MALFORMED,
            selectStatus = selectStatus
        )
        if (readStatus != "9000") return NfcPreflightResult(
            NfcPreflightOutcome.CARD_ACCESS_READ_REJECTED,
            selectStatus = selectStatus, readStatus = readStatus
        )
        // EF.CardAccess contains DER-encoded SET OF SecurityInfo (tag 31).
        val startsWithSet = read.size >= 3 && (read[0].toInt() and 0xFF) == 0x31
        return NfcPreflightResult(
            if (startsWithSet) NfcPreflightOutcome.CARD_ACCESS_OK
            else NfcPreflightOutcome.CARD_ACCESS_RESPONSE_MALFORMED,
            selectStatus = selectStatus, readStatus = readStatus
        )
    }
}

object NfcPreflightProbe {
    fun run(tag: Tag): NfcPreflightResult {
        val iso = IsoDep.get(tag) ?: return NfcPreflightResult(NfcPreflightOutcome.NON_ISODEP)
        val extended = iso.isExtendedLengthApduSupported
        val maxLength = iso.maxTransceiveLength
        try {
            iso.connect()
            iso.timeout = 5000
            val selected = iso.transceive(CardAccessApdu.select())
            if (CardAccessApdu.statusWord(selected) != "9000") {
                return CardAccessApdu.classify(selected, null)
                    .copy(extendedApdu = extended, maxTransceiveLength = maxLength)
            }
            val read = iso.transceive(CardAccessApdu.readOneByte())
            return CardAccessApdu.classify(selected, read)
                .copy(extendedApdu = extended, maxTransceiveLength = maxLength)
        } catch (_: TagLostException) {
            return NfcPreflightResult(NfcPreflightOutcome.CONNECTION_LOST, extended, maxLength)
        } catch (_: IOException) {
            return NfcPreflightResult(NfcPreflightOutcome.IO_ERROR, extended, maxLength)
        } catch (_: SecurityException) {
            return NfcPreflightResult(NfcPreflightOutcome.SECURITY_ERROR, extended, maxLength)
        } finally {
            // A preflight must never retain a handle / PACE state used by a
            // subsequent real authentication run.
            runCatching { iso.close() }
        }
    }
}
