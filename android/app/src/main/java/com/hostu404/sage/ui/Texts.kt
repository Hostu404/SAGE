package com.hostu404.sage.ui

import com.hostu404.sage.data.Country
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Words

/** The two things the map and the lists can show. */
val MEASURES = linkedMapOf("fighting" to "Deadly fighting", "major_war" to "Full-scale war")

/** "November", the month most of the app talks about. */
fun nextMonth(f: Forecast): String = f.months.getOrNull(Forecast.NEXT)?.let(Words::month) ?: "next month"

/** A country's chance of something next month. */
fun chanceOf(c: Country?, key: String): Double? = c?.outlooks?.get(key)?.chance?.getOrNull(Forecast.NEXT)

/** "News of other countries' forces massing against it far above usual since 14 October, up to about 4×." */
fun alertShort(c: Country): String {
    val a = c.alert
    val who = if (a.kind == "aimed") "other countries' forces massing against it" else "its forces mobilising and threats of force"
    return "News of $who far above usual" + (a.since?.let { " since ${Words.day(it)}" } ?: "") +
        (a.timesUsual?.let { ", up to about ${"%.0f".format(it)}×." } ?: ".")
}

/** The full alert, with how past alerts like it turned out. */
fun alertText(c: Country, f: Forecast): String {
    val a = c.alert
    val who = if (a.kind == "aimed") "other countries' forces massing against ${c.name}" else "${c.name}'s forces mobilising and threats of force"
    val since = a.since?.let { " since ${Words.day(it)}" } ?: ""
    val times = a.timesUsual?.let { ", up to about ${"%.0f".format(it)} times usual" } ?: ""
    val calm = c.lastMonth.state < 25
    val past = f.alertRecord?.questions?.get(if (calm) "fighting" else "major_war")?.takeIf { it.alerts > 0 }?.let {
        " After past alerts like this, ${if (calm) "deadly fighting" else "full-scale war"} followed within three months in ${it.followed} of ${it.alerts} cases."
    } ?: ""
    return "News of $who has been far above usual$since$times.$past An alert is a warning sign, not a forecast."
}

/** "Forecast made 29 October, from conflict data to September 2026 and news to 28 October." */
fun made(f: Forecast): String {
    val news = f.alertRecord?.checkedThrough?.takeIf { it.isNotEmpty() }?.let { " and news to ${Words.day(it)}" } ?: ""
    return "Forecast made ${Words.day(f.generated)}, from conflict data to ${Words.monthYear(f.dataThrough)}$news."
}
