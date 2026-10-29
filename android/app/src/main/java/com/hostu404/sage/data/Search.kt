package com.hostu404.sage.data

import java.text.Normalizer

/** Finding a country by what people type, as on the website: "uk", "cote", "drc". */
object Search {
    /** Other names people search by, to the country's id. */
    val ALIASES = mapOf(
        "uk" to 200, "britain" to 200, "great britain" to 200, "england" to 200, "scotland" to 200, "wales" to 200,
        "northern ireland" to 200, "us" to 2, "usa" to 2, "america" to 2, "united states" to 2, "uae" to 696,
        "emirates" to 696, "drc" to 490, "zaire" to 490, "democratic republic of the congo" to 490,
        "republic of the congo" to 484, "cote d ivoire" to 437, "burma" to 775, "czechia" to 316, "bosnia" to 346,
        "turkiye" to 640, "east timor" to 860, "dprk" to 731, "macedonia" to 343, "holland" to 210, "persia" to 630,
        "car" to 482,
    )

    fun norm(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Countries matching `text`, best first: whole names, then starts of names and words, then anywhere. */
    fun find(f: Forecast, text: String): List<Place> {
        val q = norm(text)
        if (q.isEmpty()) return emptyList()
        return f.places.mapNotNull { p ->
            val name = norm(p.name)
            var score = when {
                name == q -> 0
                name.startsWith(q) -> 1
                name.split(" ").any { it.startsWith(q) } -> 2
                name.contains(q) -> 3
                else -> 9
            }
            for ((alias, id) in ALIASES) {
                if (id == p.id && (alias == q || (q.length >= 2 && alias.startsWith(q)))) score = minOf(score, if (alias == q) 0 else 2)
            }
            if (score < 9) score to p else null
        }.sortedWith(compareBy<Pair<Int, Place>> { it.first }.thenBy { it.second.name }).map { it.second }
    }
}
