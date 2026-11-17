package com.termux.victor.pc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyPcAckGateTest {

    @Test
    fun currentVersionWithTimestampIsAcknowledged() {
        assertTrue(MyPcAckGate.isValid(MyPcAckGate.WARNING_VERSION, 1_700_000_000_000L))
    }

    @Test
    fun missingOrZeroIsNotAcknowledged() {
        assertFalse(MyPcAckGate.isValid(0, 0L))
        assertFalse(MyPcAckGate.isValid(MyPcAckGate.WARNING_VERSION, 0L))
        assertFalse(MyPcAckGate.isValid(MyPcAckGate.WARNING_VERSION, -1L))
    }

    @Test
    fun anOlderOrNewerWarningVersionAsksAgain() {
        assertFalse(MyPcAckGate.isValid(MyPcAckGate.WARNING_VERSION - 1, 1_700_000_000_000L))
        assertFalse(MyPcAckGate.isValid(MyPcAckGate.WARNING_VERSION + 1, 1_700_000_000_000L))
    }
}
