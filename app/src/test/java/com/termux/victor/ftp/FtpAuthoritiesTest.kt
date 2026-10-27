package com.termux.zerocore.ftp

import com.termux.zerocore.ftp.new_ftp.utils.FtpAuthorities
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Victorious Framework: FTP write permission follows the writable flag. */
class FtpAuthoritiesTest {
    @Test
    fun readOnlyUsersGetNoAuthorities() {
        assertTrue(FtpAuthorities.forUser(false).isEmpty())
    }

    @Test
    fun writableUsersGetWritePermission() {
        val a = FtpAuthorities.forUser(true)
        assertEquals(1, a.size)
        assertTrue(a[0] is WritePermission)
    }
}
