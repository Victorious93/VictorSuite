package com.termux.victor.hak5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Hak5SafetyTest {
    @Test
    fun redact_masksApiTokenInJson() {
        val raw = """{"system":"notifications","action":"listNotifications","apiToken":"super-secret"}"""
        val redacted = Hak5AuditLog.redact(raw)!!
        assertFalse(redacted.contains("super-secret"))
        assertTrue(redacted.contains("***"))
        assertTrue(redacted.contains("apiToken"))
    }

    @Test
    fun redact_masksBearerAndPassword() {
        assertFalse(Hak5AuditLog.redact("Authorization: Bearer abc.def.ghi")!!.contains("abc.def.ghi"))
        assertFalse(Hak5AuditLog.redact("""{"password":"hunter2"}""")!!.contains("hunter2"))
    }

    @Test
    fun actionConfirm_blocksWithoutAuth() {
        val action = Hak5PendingAction(
            id = "start_module",
            title = "Start module",
            summary = "Would start something",
            severity = Hak5ActionSeverity.POTENTIALLY_INVASIVE,
            capabilityKey = "module:Recon:start"
        )
        val reason = Hak5ActionConfirm.blockReason(
            authorized = false,
            action = action,
            capabilities = null,
            userConfirmed = true
        )
        assertTrue(reason!!.contains("Authorized-use"))
    }

    @Test
    fun actionConfirm_blocksStateChangeWithoutUserConfirm() {
        val action = Hak5PendingAction(
            id = "cfg",
            title = "Config",
            summary = "change",
            severity = Hak5ActionSeverity.CONFIG,
            capabilityKey = null
        )
        val reason = Hak5ActionConfirm.blockReason(
            authorized = true,
            action = action,
            capabilities = CapabilityMap(
                firmwareLabel = "2.7.0",
                apiShapeConfirmed = true,
                confirmedActions = emptySet(),
                limitedMode = false
            ),
            userConfirmed = false
        )
        assertTrue(reason!!.contains("Per-action confirmation"))
    }

    @Test
    fun actionConfirm_allowsReadOnlyWhenAuthorized() {
        val action = Hak5PendingAction(
            id = "status",
            title = "Status",
            summary = "read",
            severity = Hak5ActionSeverity.READ_ONLY,
            capabilityKey = PineappleCandidates.LIST_NOTIFICATIONS.key()
        )
        val caps = CapabilityMap(
            firmwareLabel = "2.7.0",
            apiShapeConfirmed = true,
            confirmedActions = setOf(PineappleCandidates.LIST_NOTIFICATIONS.key()),
            limitedMode = false
        )
        assertNull(
            Hak5ActionConfirm.blockReason(
                authorized = true,
                action = action,
                capabilities = caps,
                userConfirmed = false
            )
        )
        assertTrue(Hak5ActionConfirm.mayExecute(true, action, caps, false))
    }

    @Test
    fun actionConfirm_hidesUnconfirmedOnLimitedFirmware() {
        val action = Hak5PendingAction(
            id = "invasive",
            title = "Attacky",
            summary = "nope",
            severity = Hak5ActionSeverity.POTENTIALLY_INVASIVE,
            capabilityKey = "module:Evil:run"
        )
        val caps = CapabilityMap.unknown(apiShapeConfirmed = true)
        val reason = Hak5ActionConfirm.blockReason(true, action, caps, userConfirmed = true)
        assertTrue(reason!!.contains("limited mode") || reason.contains("not confirmed"))
    }

    @Test
    fun ackStatement_matchesProductCopy() {
        assertEquals(
            "I own this network/device or have written permission to test it.",
            Hak5AuthGate.ACK_STATEMENT
        )
    }

    @Test
    fun candidates_readOnlyProbesNeverIncludeStateChangingFlags() {
        assertTrue(PineappleCandidates.allReadOnlyProbes().all { it.readOnly })
    }
}
