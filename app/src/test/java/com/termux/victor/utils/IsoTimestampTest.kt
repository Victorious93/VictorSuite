package com.termux.victor.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class IsoTimestampTest {
    private val date = Date(1_793_116_800_123L)

    @Test
    fun offsetsMatchXxxPattern() {
        assertEquals("Z", IsoTimestamp.offset(0))
        assertEquals("-04:00", IsoTimestamp.offset(-4 * 3_600_000))
        assertEquals("+05:30", IsoTimestamp.offset(5 * 3_600_000 + 30 * 60_000))
    }

    @Test
    fun formatEqualsJvmXxxOutput() {
        for (id in listOf("UTC", "America/New_York", "Asia/Kolkata", "Asia/Shanghai")) {
            val tz = TimeZone.getTimeZone(id)
            val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply { timeZone = tz }.format(date)
            assertEquals(id, expected, IsoTimestamp.format(date, tz))
        }
    }
}
