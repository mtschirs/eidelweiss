package de.mtschirs.eidelweiss

import org.junit.Assert.*
import org.junit.Test

class Eac2FaultLabTest {
    @Test fun missingChainOccursBeforeAnyCardTaAttempt() {
        val run = Eac2FaultLab.run(LabFaultPoint.CHAIN_MISSING)
        assertFalse(run.firstTaProofAttempted)
        assertFalse(run.firstTaSucceeded)
        assertTrue(run.cardSessionPreserved)
        assertEquals(1, run.steps.last().cardSession)
        assertEquals(2, run.steps.last().serverTransaction)
    }

    @Test fun earlyFaultsLeaveSecondTaPossibleInSyntheticModel() {
        for (fault in listOf(
            LabFaultPoint.CHAIN_MISSING,
            LabFaultPoint.CERTIFICATE_REJECTED,
            LabFaultPoint.MSE_SET_AT_REJECTED
        )) {
            val result = Eac2FaultLab.run(fault)
            assertTrue("Expected synthetic second-TA success for $fault", result.secondTaAccepted)
            assertFalse(result.firstTaProofAttempted)
        }
    }

    @Test fun rejectedExternalAuthenticateBlocksConservativeRetry() {
        val run = Eac2FaultLab.run(LabFaultPoint.EXTERNAL_AUTH_REJECTED)
        assertTrue(run.firstTaProofAttempted)
        assertFalse(run.firstTaSucceeded)
        assertFalse(run.secondTaAccepted)
    }

    @Test fun caFailureDoesNotUndoFirstSuccessfulTerminalAuthentication() {
        val run = Eac2FaultLab.run(LabFaultPoint.CHIP_AUTH_REJECTED)
        assertTrue(run.firstTaSucceeded)
        assertFalse(run.secondTaAccepted)
        assertTrue(run.steps.any {
            it.operation == LabOperation.CHIP_AUTHENTICATION && it.result == LabResultCode.REJECTED
        })
    }

    @Test fun alreadySuccessfulTaBlocksDifferentTerminalUnderConservativeReference() {
        val run = Eac2FaultLab.run(LabFaultPoint.NONE, LabTerminal.SECOND)
        assertTrue(run.firstTaSucceeded)
        assertFalse(run.secondTaAccepted)
        assertTrue(run.steps.any { it.operation == LabOperation.RESTORE_PACE_MODEL })
        assertEquals(1, run.steps.last().cardSession)
        assertEquals(1, run.steps.last().successfulTaCount)
    }

    @Test fun sameTerminalRejectedEvenByPermissiveHypothesis() {
        val run = Eac2FaultLab.run(LabFaultPoint.NONE, LabTerminal.FIRST,
            LabChipPolicy.PERMISSIVE_HYPOTHESIS)
        assertFalse(run.secondTaAccepted)
    }

    @Test fun distinctTerminalAcceptedOnlyInClearlyLabeledPermissiveModel() {
        val run = Eac2FaultLab.run(LabFaultPoint.NONE, LabTerminal.SECOND,
            LabChipPolicy.PERMISSIVE_HYPOTHESIS)
        assertTrue(run.secondTaAccepted)
        assertEquals(2, run.steps.last().successfulTaCount)
        assertEquals(LabChipPolicy.PERMISSIVE_HYPOTHESIS, run.chipPolicy)
    }

    @Test fun secondServerTransactionNeverCreatesNewChipSession() {
        for (run in Eac2FaultLab.runMatrix()) {
            assertEquals(1, run.steps.first().cardSession)
            assertEquals(1, run.steps.last().cardSession)
            assertEquals(2, run.steps.last().serverTransaction)
        }
    }

    @Test fun matrixCoversEveryFaultPointAndBothPolicies() {
        val matrix = Eac2FaultLab.runMatrix()
        assertTrue(matrix.size >= LabFaultPoint.entries.size + 3)
        LabFaultPoint.entries.forEach { fault ->
            assertTrue(matrix.any { it.fault == fault })
        }
        assertTrue(matrix.any { it.chipPolicy == LabChipPolicy.PERMISSIVE_HYPOTHESIS })
        assertTrue(matrix.any { it.chipPolicy == LabChipPolicy.NPA_CONSERVATIVE_REFERENCE })
    }
}
