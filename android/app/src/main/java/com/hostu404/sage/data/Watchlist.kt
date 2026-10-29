package com.hostu404.sage.data

import org.json.JSONObject
import kotlin.math.abs

/** One country as it stood when you last marked your watchlist as seen. */
data class SeenCountry(
    val fighting: Double?,
    val war: Double?,
    val alert: Boolean,
    val status: String,
    /** Its relations that weren't "usual", by the other country's id. */
    val tensions: Map<Int, String>,
)

/** Your watchlist as you last saw it. */
data class Seen(
    val generated: String,
    val worldLevel: String?,
    val worldRank: Int?,
    val countries: Map<Int, SeenCountry>,
) {
    fun with(id: Int, f: Forecast): Seen = copy(countries = countries + (id to Watchlist.stateOf(f, id)))

    fun toJson(): String = JSONObject().apply {
        put("generated", generated)
        worldLevel?.let { put("world_level", it) }
        worldRank?.let { put("world_rank", it) }
        put("countries", JSONObject().apply {
            countries.forEach { (id, c) ->
                put(id.toString(), JSONObject().apply {
                    c.fighting?.let { put("fighting", it) }
                    c.war?.let { put("war", it) }
                    put("alert", c.alert)
                    put("status", c.status)
                    put("tensions", JSONObject().apply { c.tensions.forEach { (other, t) -> put(other.toString(), t) } })
                })
            }
        })
    }.toString()

    companion object {
        fun parse(text: String?): Seen? = text?.let {
            runCatching {
                val o = JSONObject(it)
                val cs = o.optJSONObject("countries") ?: JSONObject()
                Seen(
                    generated = o.optString("generated"),
                    worldLevel = o.optString("world_level").ifEmpty { null },
                    worldRank = if (o.has("world_rank")) o.optInt("world_rank") else null,
                    countries = cs.keys().asSequence().mapNotNull { k ->
                        val c = cs.getJSONObject(k)
                        val t = c.optJSONObject("tensions") ?: JSONObject()
                        k.toIntOrNull()?.let { id ->
                            id to SeenCountry(
                                fighting = if (c.has("fighting")) c.optDouble("fighting") else null,
                                war = if (c.has("war")) c.optDouble("war") else null,
                                alert = c.optBoolean("alert"),
                                status = c.optString("status"),
                                tensions = t.keys().asSequence().mapNotNull { o2 -> o2.toIntOrNull()?.let { it to t.optString(o2) } }.toMap(),
                            )
                        }
                    }.toMap(),
                )
            }.getOrNull()
        }
    }
}

/** Something that moved since you last looked; `worse` says which way. */
data class Change(val text: String, val worse: Boolean)

/**
 * The countries you watch (where you are, and those you follow): what moved
 * for each since you last marked them as seen. Both ways: a calmer outlook
 * is news too.
 */
object Watchlist {
    /** A chance has to move at least this far, and reach it, to be worth pointing out. */
    const val STEP = 0.02

    fun stateOf(f: Forecast, id: Int): SeenCountry {
        val c = f.place(id)?.country
        return SeenCountry(
            fighting = c?.outlooks?.get("fighting")?.chance?.getOrNull(Forecast.NEXT),
            war = c?.outlooks?.get("major_war")?.chance?.getOrNull(Forecast.NEXT),
            alert = c?.alert?.on == true,
            status = c?.status ?: "Relations only",
            tensions = f.relationsOf(id).filter { it.tension != "usual" }.associate { it.otherId(id) to it.tension },
        )
    }

    fun snapshot(f: Forecast, ids: Collection<Int>): Seen =
        Seen(f.generated, f.world?.level, f.world?.rank, ids.associateWith { stateOf(f, it) })

    fun world(seen: Seen?, f: Forecast): Change? {
        val was = seen?.worldRank ?: return null
        val now = f.world ?: return null
        if (now.rank == was) return null
        return Change("Global threat level: ${seen.worldLevel ?: "?"} → ${now.level}", now.rank > was)
    }

    fun changes(seen: Seen?, f: Forecast, id: Int): List<Change> {
        val was = seen?.countries?.get(id) ?: return emptyList()
        val now = stateOf(f, id)
        val out = mutableListOf<Change>()
        if (now.alert && !was.alert) out += Change("New build-up alert", true)
        if (!now.alert && was.alert) out += Change("Build-up alert over", false)
        if (now.status != was.status && was.status.isNotEmpty()) {
            out += Change("${now.status} last month, was ${was.status.lowercase()}", severity(now.status) > severity(was.status))
        }
        val month = f.months.getOrNull(Forecast.NEXT)?.let(Words::monthShort) ?: "next month"
        for ((label, p, old) in listOf(Triple("Deadly fighting", now.fighting, was.fighting), Triple("Full-scale war", now.war, was.war))) {
            if (p == null || old == null) continue
            if (abs(p - old) >= STEP && maxOf(p, old) >= STEP && Words.pctShort(p) != Words.pctShort(old)) {
                out += Change("$label in $month: ${Words.pctShort(old)} → ${Words.pctShort(p)}", p > old)
            }
        }
        val high = Tension.rise("high")
        for (r in f.relationsOf(id)) {
            val before = was.tensions[r.otherId(id)] ?: "usual"
            if (r.tension != before && (Tension.rise(r.tension) >= high || Tension.rise(before) >= high)) {
                out += Change("Tension with ${r.other(id)}: $before → ${r.tension}", Tension.rise(r.tension) > Tension.rise(before))
            }
        }
        return out
    }

    private fun severity(status: String) =
        listOf("Relations only", "Calm", "Attacks on civilians", "Some clashes", "Fighting", "At war").indexOf(status)
}
