package com.termux.victor.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO-8601 local timestamp with a colon offset ("2026-10-27T14:56:00.123-04:00", or "...Z" at UTC),
 * i.e. what SimpleDateFormat's "XXX" produces, but without "X", which needs API 24 (minSdk is 23).
 */
object IsoTimestamp {
    @JvmStatic
    @JvmOverloads
    fun format(date: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String {
        val base = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply { timeZone = zone }.format(date)
        return base + offset(zone.getOffset(date.time))
    }

    @JvmStatic
    fun offset(offsetMs: Int): String {
        if (offsetMs == 0) return "Z"
        val sign = if (offsetMs < 0) '-' else '+'
        val totalMinutes = Math.abs(offsetMs) / 60_000
        return String.format(Locale.US, "%c%02d:%02d", sign, totalMinutes / 60, totalMinutes % 60)
    }
}
