package com.termux.victor.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcAdbExposureTest {

    @Test
    fun tcpPortParsing() {
        assertEquals(5555, PcAdbExposure.parseTcpPort("5555\n"))
        assertNull(PcAdbExposure.parseTcpPort(null))
        assertNull(PcAdbExposure.parseTcpPort(""))
        assertNull(PcAdbExposure.parseTcpPort("\n"))
        assertNull(PcAdbExposure.parseTcpPort("-1"))
        assertNull(PcAdbExposure.parseTcpPort("0"))
        assertNull(PcAdbExposure.parseTcpPort("70000"))
        assertNull(PcAdbExposure.parseTcpPort("5555; id"))
    }

    @Test
    fun serviceWinsOverPersist() {
        assertEquals(5555, PcAdbExposure.legacyTcpPort("5555", "6666"))
        assertEquals(6666, PcAdbExposure.legacyTcpPort("-1", "6666"))
        assertEquals(6666, PcAdbExposure.legacyTcpPort("", "6666"))
        assertNull(PcAdbExposure.legacyTcpPort("-1", ""))
        assertNull(PcAdbExposure.legacyTcpPort(null, null))
    }

    @Test
    fun settingsFlags() {
        assertEquals(true, PcAdbExposure.flag(1))
        assertEquals(false, PcAdbExposure.flag(0))
        assertNull(PcAdbExposure.flag(-1))
        assertNull(PcAdbExposure.flag(null))
        assertNull(PcAdbExposure.flag(7))
    }

    @Test
    fun legacyListenerFlag() {
        assertTrue(PcAdbExposure(null, null, 5555).legacyTcpListenerOpen)
        assertFalse(PcAdbExposure(true, true, null).legacyTcpListenerOpen)
    }
}
