package com.termux.victor.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcMirrorNetTest {

    @Test
    fun privateRangesAreLan() {
        assertEquals(AddrKind.LAN, PcMirrorNet.classify("10.0.0.5"))
        assertEquals(AddrKind.LAN, PcMirrorNet.classify("10.255.255.255"))
        assertEquals(AddrKind.LAN, PcMirrorNet.classify("172.16.0.1"))
        assertEquals(AddrKind.LAN, PcMirrorNet.classify("172.31.255.254"))
        assertEquals(AddrKind.LAN, PcMirrorNet.classify("192.168.1.20"))
    }

    @Test
    fun rangeEdgesAreNotLan() {
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("172.15.255.255"))
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("172.32.0.1"))
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("192.169.0.1"))
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("11.0.0.1"))
    }

    @Test
    fun tailscaleIsTheCgnatRange() {
        assertEquals(AddrKind.TAILSCALE, PcMirrorNet.classify("100.64.0.1"))
        assertEquals(AddrKind.TAILSCALE, PcMirrorNet.classify("100.101.102.103"))
        assertEquals(AddrKind.TAILSCALE, PcMirrorNet.classify("100.127.255.254"))
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("100.63.255.255"))
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("100.128.0.1"))
    }

    @Test
    fun otherSpecialRanges() {
        assertEquals(AddrKind.LOOPBACK, PcMirrorNet.classify("127.0.0.1"))
        assertEquals(AddrKind.LINK_LOCAL, PcMirrorNet.classify("169.254.10.10"))
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("8.8.8.8"))
        assertEquals(AddrKind.PUBLIC, PcMirrorNet.classify("203.0.113.9"))
        assertEquals(AddrKind.INVALID, PcMirrorNet.classify("0.0.0.0"))
        assertEquals(AddrKind.INVALID, PcMirrorNet.classify("224.0.0.1"))
        assertEquals(AddrKind.INVALID, PcMirrorNet.classify("255.255.255.255"))
    }

    @Test
    fun onlyLanAndTailscaleAreTrusted() {
        assertTrue(PcMirrorNet.isTrusted(AddrKind.LAN))
        assertTrue(PcMirrorNet.isTrusted(AddrKind.TAILSCALE))
        for (kind in listOf(AddrKind.LOOPBACK, AddrKind.LINK_LOCAL, AddrKind.PUBLIC, AddrKind.INVALID)) {
            assertFalse(kind.name, PcMirrorNet.isTrusted(kind))
        }
    }

    @Test
    fun parsingIsStrict() {
        assertNull(PcMirrorNet.parseIpv4(""))
        assertNull(PcMirrorNet.parseIpv4("192.168.1"))
        assertNull(PcMirrorNet.parseIpv4("192.168.1.1.1"))
        assertNull(PcMirrorNet.parseIpv4("192.168.1.256"))
        assertNull(PcMirrorNet.parseIpv4("192.168.01.1"))
        assertNull(PcMirrorNet.parseIpv4("192.168.1.-1"))
        assertNull(PcMirrorNet.parseIpv4("192.168.1.1 "))
        assertNull(PcMirrorNet.parseIpv4("192.168.1.1; rm -rf ~"))
        assertNull(PcMirrorNet.parseIpv4("my-pc.local"))
        assertNull(PcMirrorNet.parseIpv4("fd7a:115c:a1e0::1"))
        assertNull(PcMirrorNet.parseIpv4("0x7f.0.0.1"))
        assertEquals(listOf(192, 168, 0, 1), PcMirrorNet.parseIpv4("192.168.0.1")!!.toList())
        assertEquals(listOf(10, 0, 0, 0), PcMirrorNet.parseIpv4("10.0.0.0")!!.toList())
    }

    @Test
    fun ports() {
        assertTrue(PcMirrorNet.isValidPort(1))
        assertTrue(PcMirrorNet.isValidPort(65535))
        assertFalse(PcMirrorNet.isValidPort(0))
        assertFalse(PcMirrorNet.isValidPort(65536))
        assertFalse(PcMirrorNet.isValidPort(-5))
    }

    @Test
    fun mobileDataInterfacesAreNotOffered() {
        assertTrue(PcMirrorNet.isMobileInterface("rmnet_data0"))
        assertTrue(PcMirrorNet.isMobileInterface("v4-rmnet_data1"))
        assertTrue(PcMirrorNet.isMobileInterface("ccmni1"))
        assertFalse(PcMirrorNet.isMobileInterface("wlan0"))
        assertFalse(PcMirrorNet.isMobileInterface("tun0"))
        assertFalse(PcMirrorNet.isMobileInterface("tailscale0"))

        val carrier = PcMirrorNet.describe("rmnet_data0", "10.45.1.2")
        assertEquals(AddrKind.LAN, carrier.kind)
        assertFalse(carrier.usable)
    }

    @Test
    fun usableHostsListWifiBeforeTailscaleAndDropUntrusted() {
        val hosts = PcMirrorNet.usableHosts(
            listOf(
                PcMirrorNet.describe("tun0", "100.100.1.1"),
                PcMirrorNet.describe("rmnet_data0", "10.45.1.2"),
                PcMirrorNet.describe("wlan0", "192.168.1.20"),
                PcMirrorNet.describe("eth9", "203.0.113.4"),
            )
        )
        assertEquals(listOf("192.168.1.20", "100.100.1.1"), hosts.map { it.ip })
    }
}
