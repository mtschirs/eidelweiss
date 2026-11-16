package de.mtschirs.eidelweiss

/**
 * Synthetic EAC control-flow model, NOT an implementation of a card or a real eID protocol.
 * We intentionally model only the sequence of authorized client-side operations.
 */
enum class EacStage {
    IDLE, PACE_ESTABLISHED, TA_PREPARING, TA_COMPLETED, EAC2_ERROR, TRANSMIT, TERMINATED
}

enum class EacEvent {
    BEGIN_PACE, BEGIN_TA, COMPLETE_TA, FAIL_EAC2, SERVER_TRANSMIT, RESET
}

enum class EacPolicy { STRICT, PERMISSIVE_REFERENCE }

data class TransitionResult(
    val before: EacStage,
    val event: EacEvent,
    val after: EacStage,
    val accepted: Boolean,
    val note: String
)

class EacScenario(private val policy: EacPolicy) {
    var stage: EacStage = EacStage.IDLE
        private set

    fun apply(event: EacEvent): TransitionResult {
        val old = stage
        val next = when {
            event == EacEvent.RESET -> EacStage.TERMINATED
            old == EacStage.IDLE && event == EacEvent.BEGIN_PACE -> EacStage.PACE_ESTABLISHED
            old == EacStage.PACE_ESTABLISHED && event == EacEvent.BEGIN_TA -> EacStage.TA_PREPARING
            old == EacStage.TA_PREPARING && event == EacEvent.COMPLETE_TA -> EacStage.TA_COMPLETED
            old == EacStage.TA_PREPARING && event == EacEvent.FAIL_EAC2 -> EacStage.EAC2_ERROR
            old == EacStage.TA_COMPLETED && event == EacEvent.SERVER_TRANSMIT -> EacStage.TRANSMIT
            old == EacStage.TRANSMIT && event == EacEvent.SERVER_TRANSMIT -> EacStage.TRANSMIT
            old == EacStage.EAC2_ERROR && event == EacEvent.SERVER_TRANSMIT &&
                policy == EacPolicy.PERMISSIVE_REFERENCE -> EacStage.TRANSMIT
            else -> null
        }
        if (next != null) stage = next
        val description = if (next == null) "rejected by synthetic policy"
            else if (old == EacStage.EAC2_ERROR && event == EacEvent.SERVER_TRANSMIT)
                "permissive model: transmit accepted after EAC2 error"
            else "accepted by synthetic policy"
        return TransitionResult(old, event, stage, next != null, description)
    }
}

data class ScenarioReport(
    val name: String,
    val policy: EacPolicy,
    val transitions: List<TransitionResult>
) {
    val anomaly: Boolean
        get() = transitions.any {
            it.before == EacStage.EAC2_ERROR &&
                it.event == EacEvent.SERVER_TRANSMIT && it.accepted
        }
}

object OfflineMatrix {
    private val afterFailure = listOf(
        EacEvent.BEGIN_PACE, EacEvent.BEGIN_TA, EacEvent.FAIL_EAC2, EacEvent.SERVER_TRANSMIT
    )
    private val successful = listOf(
        EacEvent.BEGIN_PACE, EacEvent.BEGIN_TA, EacEvent.COMPLETE_TA, EacEvent.SERVER_TRANSMIT
    )
    fun run(): List<ScenarioReport> = listOf(
        scenario("strict-failure", EacPolicy.STRICT, afterFailure),
        scenario("permissive-failure", EacPolicy.PERMISSIVE_REFERENCE, afterFailure),
        scenario("normal-success", EacPolicy.STRICT, successful),
        scenario("repeat-ta-after-success", EacPolicy.STRICT,
            listOf(EacEvent.BEGIN_PACE, EacEvent.BEGIN_TA, EacEvent.COMPLETE_TA, EacEvent.BEGIN_TA))
    )

    private fun scenario(name: String, policy: EacPolicy, events: List<EacEvent>): ScenarioReport {
        val model = EacScenario(policy)
        return ScenarioReport(name, policy, events.map(model::apply))
    }
}
