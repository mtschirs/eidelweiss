package de.mtschirs.eidelweiss

import java.time.Instant
import java.util.UUID

/** Records only SDK event types and user actions; never APDUs, IDs, PINs or payloads. */
enum class SdkEvidenceEvent {
    RUN_AUTH_REQUESTED, EAC2_NATIVE_FAULT_ARMED, SDK_AUTH_MESSAGE, SDK_ACCESS_RIGHTS, SDK_CERTIFICATE,
    SDK_INSERT_CARD, SDK_ENTER_PIN, SDK_ENTER_CAN, SDK_ENTER_PUK, SDK_STATUS,
    SDK_BAD_STATE, SDK_PAUSE, SDK_DISCONNECTED, SDK_ERROR,
    CERTIFICATE_AND_RIGHTS_DISPLAYED, USER_CONSENT_ACCEPTED, USER_CONSENT_REJECTED,
    NFC_TAG_DISPATCHED, USER_CANCEL_REQUESTED, USER_CONTINUE_REQUESTED,
    PIN_SUBMITTED, CAN_SUBMITTED, PUK_SUBMITTED,
    AUTH_FINAL_OK, AUTH_FINAL_ERROR, LOCAL_ABORT, RUN_ENDED
}
enum class SdkEvidenceSource { SDK_CALLBACK, UI_ACTION, APP_DISPATCH }
data class SdkEvidenceMark(
    val elapsedMs: Long,
    val event: SdkEvidenceEvent,
    val source: SdkEvidenceSource
)
data class SdkEvidenceRun(
    val correlationId: String,
    val startedUtc: String,
    val endedUtc: String?,
    val marks: List<SdkEvidenceMark>,
    val environment: String = SelfAuthEnvironment.PRODUCTION.name
)

/**
 * App-level observations only. This does NOT demonstrate Secure Messaging continuity or TA.
 * ID is a random correlation label for independent, authorized laboratory evidence.
 */
class SdkEvidenceRecorder(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val clockUtc: () -> String = { Instant.now().toString() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() }
) {
    private data class Pending(
        val id: String,
        val startedUtc: String,
        val baseMs: Long,
        val marks: MutableList<SdkEvidenceMark> = mutableListOf(),
        val environment: String = SelfAuthEnvironment.PRODUCTION.name
    )

    private val completed = mutableListOf<SdkEvidenceRun>()
    private var active: Pending? = null

    fun begin(environment: SelfAuthEnvironment = SelfAuthEnvironment.PRODUCTION): String {
        require(active == null) { "An SDK observation run is already active" }
        val entry = Pending(idProvider(), clockUtc(), clockMs(), environment = environment.name)
        active = entry
        mark(SdkEvidenceEvent.RUN_AUTH_REQUESTED, SdkEvidenceSource.APP_DISPATCH)
        return entry.id
    }

    fun mark(event: SdkEvidenceEvent, source: SdkEvidenceSource) {
        val entry = active ?: return
        if (entry.marks.size >= 200) return
        entry.marks += SdkEvidenceMark(
            elapsedMs = (clockMs() - entry.baseMs).coerceAtLeast(0),
            event = event,
            source = source
        )
    }

    fun receivedSdkMessage(messageType: String) {
        val selected = when (messageType) {
            "AUTH" -> SdkEvidenceEvent.SDK_AUTH_MESSAGE
            "ACCESS_RIGHTS" -> SdkEvidenceEvent.SDK_ACCESS_RIGHTS
            "CERTIFICATE" -> SdkEvidenceEvent.SDK_CERTIFICATE
            "INSERT_CARD" -> SdkEvidenceEvent.SDK_INSERT_CARD
            "ENTER_PIN" -> SdkEvidenceEvent.SDK_ENTER_PIN
            "ENTER_CAN" -> SdkEvidenceEvent.SDK_ENTER_CAN
            "ENTER_PUK" -> SdkEvidenceEvent.SDK_ENTER_PUK
            "STATUS" -> SdkEvidenceEvent.SDK_STATUS
            "BAD_STATE" -> SdkEvidenceEvent.SDK_BAD_STATE
            "PAUSE" -> SdkEvidenceEvent.SDK_PAUSE
            else -> return
        }
        mark(selected, SdkEvidenceSource.SDK_CALLBACK)
    }

    fun end() {
        val entry = active ?: return
        mark(SdkEvidenceEvent.RUN_ENDED, SdkEvidenceSource.APP_DISPATCH)
        completed += SdkEvidenceRun(entry.id, entry.startedUtc, clockUtc(), entry.marks.toList(),
            entry.environment)
        if (completed.size > 20) completed.removeAt(0)
        active = null
    }

    fun snapshot(): List<SdkEvidenceRun> {
        val runs = completed.toMutableList()
        val current = active
        if (current != null) runs += SdkEvidenceRun(
            current.id, current.startedUtc, null, current.marks.toList(), current.environment
        )
        return runs
    }
}
