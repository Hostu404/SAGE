package com.hostu404.sage.data

/** Something worth pointing out about a country you follow. */
data class Notice(val countryId: Int, val title: String, val text: String)

/**
 * What got worse for the countries you follow between the forecast you last
 * saw and a new one, shown at the top of the home screen. A calmer outlook
 * shows in the forecast itself.
 */
object Changes {
    /** Chances below this aren't news, even when their word changes. */
    const val WORTH_TELLING = 0.05
    private val WATCHED = listOf("fighting", "major_war")

    /** Notices about the whole world rather than one country carry this id. */
    const val WORLD = -1

    fun between(old: Forecast, new: Forecast, followed: Set<Int>): List<Notice> {
        val notices = mutableListOf<Notice>()
        val worldWas = old.world
        val worldNow = new.world
        if (worldWas != null && worldNow != null && worldNow.rank > worldWas.rank) {
            notices += Notice(WORLD, "Global threat level up: ${worldNow.level}", "It was ${worldWas.level}. ${worldNow.text}")
        }
        for (id in followed) {
            val before = old.countries.firstOrNull { it.id == id }
            val now = new.countries.firstOrNull { it.id == id }
            if (now != null) {
                if (now.alert.on && before?.alert?.on != true) notices += alertNotice(now)
                for (key in WATCHED) risen(before, now, key, new)?.let { notices += it }
            }
            notices += tenser(old, new, id)
        }
        return notices
    }

    private fun alertNotice(c: Country): Notice {
        val who = if (c.alert.kind == "aimed") "other countries' forces massing against ${c.name}"
        else "${c.name}'s forces mobilising and threats of force"
        val times = c.alert.timesUsual?.let { " (about ${"%.0f".format(it)} times usual)" } ?: ""
        return Notice(c.id, "Build-up alert: ${c.name}",
            "News of $who is far above usual$times. An alert is a warning sign, not a forecast.")
    }

    private fun risen(before: Country?, now: Country, key: String, forecast: Forecast): Notice? {
        val p = now.outlooks[key]?.chance?.getOrNull(Forecast.NEXT) ?: return null
        val was = before?.outlooks?.get(key)?.chance?.getOrNull(Forecast.NEXT) ?: return null
        if (p < WORTH_TELLING || wordRank(p) <= wordRank(was)) return null
        val what = forecast.questions[key]?.title?.lowercase() ?: key
        val month = forecast.months.getOrNull(Forecast.NEXT)?.let { " in ${Words.month(it)}" } ?: ""
        return Notice(now.id, "${now.name}: risk up",
            "${what.replaceFirstChar { it.uppercase() }}$month is now ${Words.chance(p)} (${Words.pct(p)}), " +
                "up from ${Words.chance(was)} (${Words.pct(was)}).")
    }

    private fun tenser(old: Forecast, new: Forecast, id: Int): List<Notice> {
        val before = old.relationsOf(id).associateBy { it.otherId(id) }
        return new.relationsOf(id).mapNotNull { r ->
            val was = before[r.otherId(id)]?.tension ?: "usual"
            // "High" and above are worth pointing out; "above usual" isn't.
            if (Tension.rise(r.tension) >= Tension.rise("high") && Tension.rise(r.tension) > Tension.rise(was)) {
                val name = new.place(id)?.name ?: return@mapNotNull null
                Notice(id, "$name and ${r.other(id)}: tension ${r.tension}",
                    "Tension between $name and ${r.other(id)} is now ${r.tension} (it was ${was}). " +
                        "Deadly clashes in the next six months: ${Words.chance(r.clash6)} (${Words.pct(r.clash6)}).")
            } else null
        }
    }

    /** Position of a chance's word on SAGE's scale, 0 = extremely unlikely. */
    private fun wordRank(p: Double): Int = listOf(0.01, 0.05, 0.2, 0.4, 0.6, 0.8, 0.95).count { p >= it }
}
