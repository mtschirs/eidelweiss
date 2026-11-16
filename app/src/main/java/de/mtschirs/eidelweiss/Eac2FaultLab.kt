package de.mtschirs.eidelweiss

/**
 * Deterministic, fully offline reference models for EAC2 and repeated TA.
 *
 * No APDUs, keys, cards, service URLs, signatures or Android SDK calls are handled here.
 * These models encode hypotheses, not observed behavior of a real nPA.
 */
enum class LabFaultPoint(val displayName: String, val stage: String) {
    NONE("Kein Fehler: erste TA und CA erfolgreich", "NO_FAILURE"),
    CHAIN_MISSING("Vor TA: CVC-Kette fehlt", "CLIENT_PRE_TA"),
    CERTIFICATE_REJECTED("Vor TA: PSO Verify Certificate abgewiesen", "CARD_CERTIFICATE"),
    MSE_SET_AT_REJECTED("TA-Vorbereitung: MSE Set AT abgewiesen", "CARD_TA_SETUP"),
    EXTERNAL_AUTH_REJECTED("Während TA: External Authenticate abgewiesen", "CARD_TA_PROOF"),
    CHIP_AUTH_REJECTED("Nach TA: Chip Authentication fehlgeschlagen", "CARD_CA");
}

enum class LabTerminal {
    FIRST, SECOND
}

enum class LabChipPolicy {
    /** Conservative model: a completed TA or failed EXTERNAL AUTHENTICATE consumes the TA slot. */
    NPA_CONSERVATIVE_REFERENCE,

    /**
     * Purely hypothetical model that permits a distinct second terminal after an earlier success.
     * This is NOT a claim that German eID cards accept such a transition.
     */
    PERMISSIVE_HYPOTHESIS
}

enum class LabSecureMessaging {
    ABSENT, PACE, CA
}

enum class LabOperation {
    PACE_ESTABLISHED,
    CERTIFICATE_CHAIN_SELECTED,
    CERTIFICATE_VERIFIED,
    TA_SETUP,
    EXTERNAL_AUTHENTICATE,
    CHIP_AUTHENTICATION,
    CLIENT_EAC2_RESPONSE,
    SERVER_TRANSACTION_RESTART,
    RESTORE_PACE_MODEL,
    SECOND_TERMINAL_AUTHENTICATION,
    CARD_SESSION_RESET
}

enum class LabResultCode {
    SUCCESS, REJECTED, FAILED, NOT_EXECUTED, SYNTHETIC_ONLY
}

data class LabTraceStep(
    val index: Int,
    val serverTransaction: Int,
    val cardSession: Int,
    val operation: LabOperation,
    val result: LabResultCode,
    val secureMessaging: LabSecureMessaging,
    val successfulTaCount: Int,
    val note: String
)

data class LabTrial(
    val fault: LabFaultPoint,
    val chipPolicy: LabChipPolicy,
    val nextTerminal: LabTerminal,
    val firstTaSucceeded: Boolean,
    val secondTaAccepted: Boolean,
    val firstTaProofAttempted: Boolean,
    val cardSessionPreserved: Boolean,
    val steps: List<LabTraceStep>
) {
    val id: String get() =
        "${fault.name.lowercase()}__${chipPolicy.name.lowercase()}__${nextTerminal.name.lowercase()}"
}

object Eac2FaultLab {
    private class Model(
        private val fault: LabFaultPoint,
        private val policy: LabChipPolicy,
        private val secondTerminal: LabTerminal
    ) {
        private val log = mutableListOf<LabTraceStep>()
        private var transaction = 1
        private var cardSession = 1
        private var secureMessaging = LabSecureMessaging.ABSENT
        private var successfulTas = 0
        private var terminal = LabTerminal.FIRST
        private var externalAuthenticateAttempted = false
        private var secondAccepted = false
        private var cardSessionPreserved = true

        private fun record(
            operation: LabOperation,
            result: LabResultCode,
            note: String
        ) {
            log += LabTraceStep(
                index = log.size + 1,
                serverTransaction = transaction,
                cardSession = cardSession,
                operation = operation,
                result = result,
                secureMessaging = secureMessaging,
                successfulTaCount = successfulTas,
                note = note
            )
        }

        fun execute(): LabTrial {
            secureMessaging = LabSecureMessaging.PACE
            record(LabOperation.PACE_ESTABLISHED, LabResultCode.SUCCESS,
                "Simulierter PACE-Kontext; keine Karte beteiligt")

            when (fault) {
                LabFaultPoint.CHAIN_MISSING -> {
                    record(LabOperation.CERTIFICATE_CHAIN_SELECTED, LabResultCode.FAILED,
                        "Client findet keine CVC-Kette; keine TA-APDU erzeugt")
                }
                else -> {
                    record(LabOperation.CERTIFICATE_CHAIN_SELECTED, LabResultCode.SUCCESS,
                        "Test-CVC-Kette nur als Modellzustand vorhanden")
                    if (fault == LabFaultPoint.CERTIFICATE_REJECTED) {
                        record(LabOperation.CERTIFICATE_VERIFIED, LabResultCode.REJECTED,
                            "Simulierter Chip weist CVC zurück")
                    } else {
                        record(LabOperation.CERTIFICATE_VERIFIED, LabResultCode.SUCCESS,
                            "CVC-Prüfung im Modell erfolgreich")
                        if (fault == LabFaultPoint.MSE_SET_AT_REJECTED) {
                            record(LabOperation.TA_SETUP, LabResultCode.REJECTED,
                                "Simuliertes MSE Set AT scheitert vor EXTERNAL AUTHENTICATE")
                        } else {
                            record(LabOperation.TA_SETUP, LabResultCode.SUCCESS,
                                "TA-Setup im Modell akzeptiert")
                            externalAuthenticateAttempted = true
                            if (fault == LabFaultPoint.EXTERNAL_AUTH_REJECTED) {
                                record(LabOperation.EXTERNAL_AUTHENTICATE, LabResultCode.REJECTED,
                                    "Erste TA-Signaturprüfung scheitert; TA-Status chipabhängig")
                            } else {
                                successfulTas++
                                record(LabOperation.EXTERNAL_AUTHENTICATE, LabResultCode.SUCCESS,
                                    "Erste TA im Modell abgeschlossen")
                                if (fault == LabFaultPoint.CHIP_AUTH_REJECTED) {
                                    record(LabOperation.CHIP_AUTHENTICATION, LabResultCode.REJECTED,
                                        "CA scheitert nach erfolgreicher TA")
                                } else {
                                    secureMessaging = LabSecureMessaging.CA
                                    record(LabOperation.CHIP_AUTHENTICATION, LabResultCode.SUCCESS,
                                        "CA-Secure-Messaging im Modell aktiv")
                                }
                            }
                        }
                    }
                }
            }
            record(LabOperation.CLIENT_EAC2_RESPONSE,
                if (fault == LabFaultPoint.NONE) LabResultCode.SUCCESS else LabResultCode.FAILED,
                if (fault == LabFaultPoint.NONE) "Erster EAC2-Vorgang abgeschlossen"
                else "Fehlerstatus gemeldet; bestehende Chip-Sitzung hypothetisch erhalten")

            // Server transport is deliberately NOT connected to a real network or SDK.
            transaction++
            record(LabOperation.SERVER_TRANSACTION_RESTART, LabResultCode.SYNTHETIC_ONLY,
                "Nur neuer simulierter Servervorgang; cardSession bleibt unverändert")

            // In a successful CA flow the chip no longer uses PACE SM. Restoring an
            // earlier PACE context is simulated; doing so must not erase TA history.
            if (secureMessaging == LabSecureMessaging.CA) {
                secureMessaging = LabSecureMessaging.PACE
                record(LabOperation.RESTORE_PACE_MODEL, LabResultCode.SYNTHETIC_ONLY,
                    "Hypothetische PACE-Wiederherstellung; TA-Historie nicht zurückgesetzt")
            }

            val rejection = when {
                secureMessaging != LabSecureMessaging.PACE ->
                    "Kein PACE-Kontext verfügbar"
                successfulTas > 0 && secondTerminal == terminal ->
                    "Gleicher Terminalschlüssel: zweite TA im Modell unzulässig"
                successfulTas > 0 && policy == LabChipPolicy.NPA_CONSERVATIVE_REFERENCE ->
                    "Konservatives nPA-Modell: nur eine erfolgreiche TA pro Chip-Sitzung"
                externalAuthenticateAttempted && successfulTas == 0 &&
                    policy == LabChipPolicy.NPA_CONSERVATIVE_REFERENCE ->
                    "Konservatives Modell: vorheriger fehlgeschlagener TA-Beweis sperrt Wiederholung"
                else -> null
            }
            if (rejection == null) {
                successfulTas++
                terminal = secondTerminal
                secondAccepted = true
                record(LabOperation.SECOND_TERMINAL_AUTHENTICATION, LabResultCode.SUCCESS,
                    "Nur synthetische TA-Akzeptanz; KEINE echte TA-Signatur erzeugt")
            } else {
                record(LabOperation.SECOND_TERMINAL_AUTHENTICATION, LabResultCode.REJECTED, rejection)
            }

            val firstSuccess = fault == LabFaultPoint.NONE ||
                fault == LabFaultPoint.CHIP_AUTH_REJECTED
            return LabTrial(
                fault = fault,
                chipPolicy = policy,
                nextTerminal = secondTerminal,
                firstTaSucceeded = firstSuccess,
                secondTaAccepted = secondAccepted,
                firstTaProofAttempted = externalAuthenticateAttempted,
                cardSessionPreserved = cardSessionPreserved && cardSession == 1,
                steps = log.toList()
            )
        }
    }

    fun run(
        fault: LabFaultPoint,
        nextTerminal: LabTerminal = LabTerminal.SECOND,
        policy: LabChipPolicy = LabChipPolicy.NPA_CONSERVATIVE_REFERENCE
    ): LabTrial = Model(fault, policy, nextTerminal).execute()

    fun runMatrix(): List<LabTrial> = buildList {
        // Each EAC2 error, under the conservative reference policy.
        LabFaultPoint.entries.forEach { fault ->
            add(run(fault))
        }
        // Same-terminal replay and hypothetical different-terminal comparison.
        add(run(LabFaultPoint.NONE, LabTerminal.FIRST))
        add(run(LabFaultPoint.NONE, LabTerminal.SECOND, LabChipPolicy.PERMISSIVE_HYPOTHESIS))
        add(run(LabFaultPoint.EXTERNAL_AUTH_REJECTED, LabTerminal.SECOND,
            LabChipPolicy.PERMISSIVE_HYPOTHESIS))
    }
}
