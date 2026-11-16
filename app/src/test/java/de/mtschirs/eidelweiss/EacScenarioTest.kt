package de.mtschirs.eidelweiss

import org.junit.Assert.*
import org.junit.Test

class EacScenarioTest {
    @Test fun strictFailureNeverForwardsTransmit() {
        val trace = OfflineMatrix.run().first { it.name == "strict-failure" }
        assertFalse(trace.anomaly)
        assertFalse(trace.transitions.last().accepted)
        assertEquals(EacStage.EAC2_ERROR, trace.transitions.last().after)
    }

    @Test fun permissiveReferenceDemonstratesSuspectedTransition() {
        val trace = OfflineMatrix.run().first { it.name == "permissive-failure" }
        assertTrue(trace.anomaly)
        assertTrue(trace.transitions.last().accepted)
        assertEquals(EacStage.TRANSMIT, trace.transitions.last().after)
    }

    @Test fun normalFlowTransmitsOnlyAfterSuccessfulTa() {
        val trace = OfflineMatrix.run().first { it.name == "normal-success" }
        assertTrue(trace.transitions.all { it.accepted })
        assertEquals(EacStage.TRANSMIT, trace.transitions.last().after)
    }

    @Test fun repeatTerminalAuthenticationIsRejectedByStrictPolicy() {
        val trace = OfflineMatrix.run().first { it.name == "repeat-ta-after-success" }
        assertFalse(trace.transitions.last().accepted)
        assertEquals(EacStage.TA_COMPLETED, trace.transitions.last().after)
    }

    @Test fun resetTerminatesModel() {
        val model = EacScenario(EacPolicy.STRICT)
        assertTrue(model.apply(EacEvent.BEGIN_PACE).accepted)
        assertTrue(model.apply(EacEvent.RESET).accepted)
        assertEquals(EacStage.TERMINATED, model.stage)
        assertFalse(model.apply(EacEvent.BEGIN_TA).accepted)
    }
}
