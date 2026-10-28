package com.hostu404.sage.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * SAGE's forecast file (forecasts/latest.json in the SAGE project), as the
 * app needs it. Every field the app doesn't use is ignored, and every field
 * it does use has a safe default, so an older or newer file still opens.
 */
data class Forecast(
    val generated: String,
    /** The last month of conflict data, e.g. "2026-09". */
    val dataThrough: String,
    /** The three months forecast; [NEXT] is "next month". */
    val months: List<String>,
    val questions: Map<String, Question>,
    val summary: List<String>,
    val record: Record?,
    val alertRecord: AlertRecord?,
    val countries: List<Country>,
    val pairs: List<Relation>,
    /** Each country's ISO code ("FRA"), by id. */
    val iso3: Map<Int, String> = emptyMap(),
) {
    /** Every country the app can show: those SAGE forecasts, plus those it only follows relations for. */
    val places: List<Place> by lazy {
        val byId = LinkedHashMap<Int, Place>()
        countries.forEach { byId[it.id] = Place(it.id, it.name, it) }
        pairs.forEach { p ->
            if (p.a !in byId) byId[p.a] = Place(p.a, p.names[0], null)
            if (p.b !in byId) byId[p.b] = Place(p.b, p.names[1], null)
        }
        byId.values.sortedBy { it.name }
    }

    fun place(id: Int): Place? = places.firstOrNull { it.id == id }

    /** The country for a two-letter code such as "FR", if SAGE has anything on it. */
    fun placeForIso2(iso2: String): Place? {
        val code = WhereYouAre.iso3(iso2) ?: return null
        val id = iso3.entries.firstOrNull { it.value == code }?.key ?: return null
        return place(id)
    }

    /** This country's relations, tensest first. */
    fun relationsOf(id: Int): List<Relation> =
        pairs.filter { it.a == id || it.b == id }
            .sortedWith(compareBy<Relation> { Tension.order(it.tension) }.thenByDescending { it.clash6 })

    companion object {
        /** Index of "next month" among the three months, as in SAGE's own summaries. */
        const val NEXT = 1
        val QUESTION_KEYS = listOf("fighting", "major_war", "attacks")

        fun parse(text: String): Forecast {
            val o = JSONObject(text)
            val questions = o.optJSONObject("questions")?.let { q ->
                q.keys().asSequence().associateWith { k ->
                    val v = q.getJSONObject(k)
                    Question(v.optString("title", k), v.optString("text"))
                }
            } ?: emptyMap()
            val pairs = o.optJSONObject("pairs")?.optJSONArray("pairs")
            return Forecast(
                generated = o.optString("generated"),
                dataThrough = o.optString("data_through"),
                months = o.optJSONArray("months").strings(),
                questions = questions,
                summary = o.optJSONArray("summary").strings(),
                record = o.optJSONObject("record")?.let(Record::parse),
                alertRecord = o.optJSONObject("alert_record")?.let(AlertRecord::parse),
                countries = o.optJSONArray("countries").objects().map(Country::parse),
                pairs = pairs.objects().map(Relation::parse),
                iso3 = o.optJSONObject("iso3")?.let { m ->
                    m.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to m.optString(k) } }.toMap()
                } ?: emptyMap(),
            )
        }
    }
}

data class Question(val title: String, val text: String)

data class Place(val id: Int, val name: String, val country: Country?)

data class Country(
    val id: Int,
    val name: String,
    val region: String,
    val lastMonth: LastMonth,
    val alert: Alert,
    /** "fighting", "major_war", "attacks" -> the outlook for each. */
    val outlooks: Map<String, Outlook>,
    val summary: String,
) {
    /** A word for how things stand, from last month's deaths. */
    val status: String
        get() = when {
            lastMonth.state >= 1000 -> "At war"
            lastMonth.state >= 25 -> "Fighting"
            lastMonth.state > 0 -> "Some clashes"
            lastMonth.groupCiv >= 10 -> "Attacks on civilians"
            else -> "Calm"
        }

    companion object {
        fun parse(o: JSONObject): Country {
            val last = o.optJSONObject("last_month") ?: JSONObject()
            val outlooks = Forecast.QUESTION_KEYS.mapNotNull { k ->
                o.optJSONObject(k)?.let { k to Outlook.parse(it) }
            }.toMap()
            return Country(
                id = o.optInt("id"),
                name = o.optString("name"),
                region = o.optString("region"),
                lastMonth = LastMonth(last.optInt("state"), last.optInt("nonstate"),
                    last.optInt("gov_civ"), last.optInt("group_civ")),
                alert = Alert.parse(o.optJSONObject("alert")),
                outlooks = outlooks,
                summary = o.optString("summary"),
            )
        }
    }
}

/** Deaths last month: in fighting involving the government, between armed groups, and of civilians. */
data class LastMonth(val state: Int, val nonstate: Int, val govCiv: Int, val groupCiv: Int)

data class Alert(
    val on: Boolean,
    val since: String? = null,
    /** "aimed": other countries' forces against this one. */
    val kind: String? = null,
    val timesUsual: Double? = null,
) {
    companion object {
        fun parse(o: JSONObject?): Alert = if (o == null || !o.optBoolean("on")) Alert(false) else Alert(
            on = true,
            since = o.optString("since").ifEmpty { null },
            kind = o.optString("kind").ifEmpty { null },
            timesUsual = o.optDouble("times_usual").takeUnless { it.isNaN() },
        )
    }
}

data class Outlook(
    /** The chance for each of the three months. */
    val chance: List<Double>,
    /** How often it happens to countries in the same situation. */
    val usual: List<Double>,
    val why: List<String>,
) {
    companion object {
        fun parse(o: JSONObject) = Outlook(o.optJSONArray("chance").doubles(), o.optJSONArray("usual").doubles(),
            o.optJSONArray("why").strings())
    }
}

/** Two countries: how tense things are and the chance their forces clash. */
data class Relation(
    val a: Int,
    val b: Int,
    val names: List<String>,
    val tension: String,
    val clash6: Double,
    val war6: Double,
    val lastFought: String?,
    val summary: String,
) {
    fun other(id: Int): String = if (id == a) names[1] else names[0]
    fun otherId(id: Int): Int = if (id == a) b else a
    val title: String get() = "${names[0]} and ${names[1]}"

    companion object {
        fun parse(o: JSONObject): Relation {
            val names = o.optJSONArray("names").strings().let { if (it.size == 2) it else listOf("?", "?") }
            return Relation(
                a = o.optInt("a"), b = o.optInt("b"), names = names,
                tension = o.optString("tension", "usual"),
                clash6 = o.optDouble("clash_6", 0.0), war6 = o.optDouble("war_6", 0.0),
                lastFought = if (o.isNull("last_fought")) null else o.optString("last_fought").ifEmpty { null },
                summary = o.optString("summary"),
            )
        }
    }
}

/** How SAGE did when its past was replayed. */
data class Record(val from: String, val to: String, val scores: Map<String, Score>) {
    companion object {
        fun parse(o: JSONObject): Record {
            val s = o.optJSONObject("scores") ?: JSONObject()
            return Record(o.optString("from"), o.optString("to"), s.keys().asSequence().associateWith { k ->
                val v = s.getJSONObject(k)
                Score(v.optDouble("auc"), v.optDouble("better_than_last_month"))
            })
        }
    }
}

/** auc: how often it rated a place where it happened above one where it didn't. */
data class Score(val auc: Double, val betterThanLastMonth: Double)

/** How past build-up alerts turned out; [checkedThrough] is the last day of news read. */
data class AlertRecord(val episodes: Int, val checkedThrough: String, val questions: Map<String, AlertOutcome>) {
    companion object {
        fun parse(o: JSONObject): AlertRecord {
            val q = o.optJSONObject("questions") ?: JSONObject()
            return AlertRecord(o.optInt("episodes"), o.optString("checked_through"), q.keys().asSequence().associateWith { k ->
                val v = q.getJSONObject(k)
                AlertOutcome(v.optInt("alerts"), v.optInt("followed"), v.optDouble("usual"))
            })
        }
    }
}

data class AlertOutcome(val alerts: Int, val followed: Int, val usual: Double)

/** Words SAGE uses for how tense two countries are, most tense first. */
object Tension {
    val LEVELS = listOf("at war", "fighting", "very high", "high", "above usual", "usual")
    fun order(t: String): Int = LEVELS.indexOf(t).let { if (it < 0) LEVELS.size else it }
    /** Higher is more tense: 0 = usual. */
    fun rise(t: String): Int = LEVELS.size - 1 - order(t).coerceAtMost(LEVELS.size - 1)
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun JSONArray?.doubles(): List<Double> =
    if (this == null) emptyList() else (0 until length()).map { optDouble(it, 0.0) }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
