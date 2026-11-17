package com.termux.victor.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcMirrorCommandsTest {

    private fun ready(plan: MirrorPlan): MirrorPlan.Ready = plan as MirrorPlan.Ready

    @Test
    fun wifiPlanHasPairConnectScrcpyAndDisconnect() {
        val p = ready(PcMirrorCommands.plan("192.168.1.20", 37123, 40001))
        assertEquals(AddrKind.LAN, p.kind)
        assertEquals("adb pair 192.168.1.20:40001", p.pair)
        assertEquals("adb connect 192.168.1.20:37123", p.connect)
        assertEquals("scrcpy -s 192.168.1.20:37123", p.scrcpy)
        assertEquals("adb disconnect 192.168.1.20:37123", p.disconnect)
    }

    @Test
    fun tailscalePlanIsAccepted() {
        val p = ready(PcMirrorCommands.plan("100.101.102.103", 5555))
        assertEquals(AddrKind.TAILSCALE, p.kind)
        assertNull(p.pair)
        assertEquals("scrcpy -s 100.101.102.103:5555", p.scrcpy)
    }

    @Test
    fun hostIsTrimmed() {
        val p = ready(PcMirrorCommands.plan("  10.0.0.7 ", 40000))
        assertEquals("10.0.0.7", p.host)
        assertEquals("adb connect 10.0.0.7:40000", p.connect)
    }

    @Test
    fun publicAndSpecialAddressesAreRefused() {
        for (host in listOf("8.8.8.8", "203.0.113.5", "100.128.0.1", "127.0.0.1", "169.254.1.1")) {
            assertEquals(
                host,
                MirrorPlan.Rejected(MirrorRejection.NOT_TRUSTED_NETWORK),
                PcMirrorCommands.plan(host, 40000),
            )
        }
    }

    @Test
    fun hostNamesAndInjectionAreRefused() {
        for (host in listOf("", "my-pc", "phone.local", "192.168.1.5;reboot", "192.168.1.5 && id", "\$(id)", "192.168.1.5\n8.8.8.8")) {
            assertEquals(
                host,
                MirrorPlan.Rejected(MirrorRejection.BAD_HOST),
                PcMirrorCommands.plan(host, 40000),
            )
        }
    }

    @Test
    fun badPortsAreRefused() {
        assertEquals(MirrorPlan.Rejected(MirrorRejection.BAD_ADB_PORT), PcMirrorCommands.plan("10.0.0.1", null))
        assertEquals(MirrorPlan.Rejected(MirrorRejection.BAD_ADB_PORT), PcMirrorCommands.plan("10.0.0.1", 0))
        assertEquals(MirrorPlan.Rejected(MirrorRejection.BAD_ADB_PORT), PcMirrorCommands.plan("10.0.0.1", 22))
        assertEquals(MirrorPlan.Rejected(MirrorRejection.BAD_ADB_PORT), PcMirrorCommands.plan("10.0.0.1", 1023))
        assertEquals(MirrorPlan.Rejected(MirrorRejection.BAD_ADB_PORT), PcMirrorCommands.plan("10.0.0.1", 70000))
        assertEquals(MirrorPlan.Rejected(MirrorRejection.BAD_PAIRING_PORT), PcMirrorCommands.plan("10.0.0.1", 40000, 80))
        assertEquals(MirrorPlan.Rejected(MirrorRejection.BAD_PAIRING_PORT), PcMirrorCommands.plan("10.0.0.1", 40000, 99999))
        assertTrue(PcMirrorCommands.plan("10.0.0.1", 1024) is MirrorPlan.Ready)
        assertTrue(PcMirrorCommands.plan("10.0.0.1", 65535) is MirrorPlan.Ready)
    }

    @Test
    fun portParsing() {
        assertEquals(5555, PcMirrorCommands.parsePort("5555"))
        assertEquals(5555, PcMirrorCommands.parsePort(" 5555 "))
        assertNull(PcMirrorCommands.parsePort(null))
        assertNull(PcMirrorCommands.parsePort(""))
        assertNull(PcMirrorCommands.parsePort("55a5"))
        assertNull(PcMirrorCommands.parsePort("-1"))
        assertNull(PcMirrorCommands.parsePort("123456"))
    }

    @Test
    fun scriptNumbersStepsAndHasNoSecrets() {
        val withPair = ready(PcMirrorCommands.plan("192.168.1.20", 37123, 40001)).asScript()
        assertTrue(withPair.contains("# 1. Pair once"))
        assertTrue(withPair.contains("# 2. Connect:"))
        assertTrue(withPair.contains("# 3. Mirror and control the phone:"))
        assertTrue(withPair.endsWith("adb disconnect 192.168.1.20:37123"))

        val withoutPair = ready(PcMirrorCommands.plan("192.168.1.20", 37123)).asScript()
        assertFalse(withoutPair.contains("adb pair"))
        assertTrue(withoutPair.contains("# 1. Connect:"))
        assertTrue(withoutPair.contains("# 2. Mirror and control the phone:"))

        for (script in listOf(withPair, withoutPair)) {
            assertTrue(script.contains("Run these on the PC"))
            assertFalse(script.lowercase().contains("password"))
            assertFalse(script.lowercase().contains("token"))
        }
    }
}
