package com.hostu404.sage.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The same words and rounding SAGE uses in its summaries, so the app and the text agree. */
object Words {
    fun chance(p: Double): String = when {
        p >= 0.95 -> "almost certain"
        p >= 0.8 -> "very likely"
        p >= 0.6 -> "likely"
        p >= 0.4 -> "about as likely as not"
        p >= 0.2 -> "a real possibility"
        p >= 0.05 -> "unlikely"
        p >= 0.01 -> "very unlikely"
        else -> "extremely unlikely"
    }

    fun pct(p: Double): String = when {
        p >= 0.995 -> "over 99%"
        p < 0.005 -> "under 1%"
        else -> "${(p * 100).roundToInt()}%"
    }

    /** 0 = unlikely or less, 1 = a real possibility, 2 = as likely as not or likely, 3 = very likely or more. */
    fun level(p: Double): Int = when {
        p >= 0.8 -> 3
        p >= 0.4 -> 2
        p >= 0.2 -> 1
        else -> 0
    }

    /** "2026-11" -> "November". */
    fun month(m: String): String = runCatching {
        YearMonth.parse(m).format(DateTimeFormatter.ofPattern("MMMM", Locale.UK))
    }.getOrDefault(m)

    /** "2026-09" -> "September 2026". */
    fun monthYear(m: String): String = runCatching {
        YearMonth.parse(m).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK))
    }.getOrDefault(m)

    /** "2026-10-28T18:07:57Z" or "2026-10-12" -> "28 October". */
    fun day(stamp: String): String = runCatching {
        val date = if (stamp.length > 10) Instant.parse(stamp).atZone(ZoneId.of("UTC")).toLocalDate()
        else java.time.LocalDate.parse(stamp)
        date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.UK))
    }.getOrDefault(stamp)
}
