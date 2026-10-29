package com.hostu404.sage

import com.hostu404.sage.data.Alert
import com.hostu404.sage.data.Change
import com.hostu404.sage.data.Country
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.LastMonth
import com.hostu404.sage.data.Outlook
import com.hostu404.sage.data.Question
import com.hostu404.sage.data.Relation
import com.hostu404.sage.data.Search
import com.hostu404.sage.data.Seen
import com.hostu404.sage.data.Tension
import com.hostu404.sage.data.Watchlist
import com.hostu404.sage.data.WhereYouAre
import com.hostu404.sage.data.Words
import com.hostu404.sage.data.World
import com.hostu404.sage.ui.Share
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The forecast the app ships with, read the way the app reads it. */
fun bundledForecast(): Forecast = Forecast.parse(File("src/main/assets/latest.json").readText())

class ForecastTest {
    @Test fun readsTheRealForecastFile() {
        val f = bundledForecast()
        assertEquals(3, f.months.size)
        assertEquals(130, f.countries.size)
        assertTrue(f.summary.isNotEmpty())
        val uk = f.place(200)?.country
        assertNotNull(uk)
        assertEquals("United Kingdom", uk!!.name)
        assertEquals(3, uk.outlooks["fighting"]!!.chance.size)
        assertNotNull(f.record?.scores?.get("fighting_1"))
        // Countries SAGE only follows relations for are there too, without a forecast of their own.
        val estonia = f.places.first { it.name == "Estonia" }
        assertNull(estonia.country)
        assertTrue(f.relationsOf(estonia.id).isNotEmpty())
    }

    @Test fun findsTheCountryYoureIn() {
        val f = bundledForecast()
        assertEquals("France", f.placeForIso2("FR")?.name)
        assertEquals("United Kingdom", f.placeForIso2("GB")?.name)
        assertEquals("Serbia", f.placeForIso2("RS")?.name)
        // Iceland: nothing on it in SAGE, so the app shows a plain line instead.
        assertNull(f.placeForIso2("IS"))
        assertEquals("Iceland", WhereYouAre.name("IS"))
    }

    @Test fun readsCloudflaresAnswer() {
        assertEquals("FR", WhereYouAre.parse("fl=1\nip=203.0.113.9\nts=1.2\nloc=FR\ntls=TLSv1.3\n"))
        assertNull(WhereYouAre.parse("loc=XX\n"))
        assertNull(WhereYouAre.parse("loc=T1\n"))
        assertNull(WhereYouAre.parse("<html>blocked</html>"))
    }

    @Test fun readsTheGlobalThreatLevel() {
        val w = bundledForecast().world!!
        assertEquals("High", w.level)
        assertEquals(3, w.rank)
        assertTrue(w.text.startsWith("Global threat level: High."))
        assertEquals("2026-11", w.month)
        assertEquals("2026-09", w.lastMonth)
        assertNotNull(w.lastFighting)
        assertNotNull(bundledForecast().relation(369, 365)?.usual6)
    }

    @Test fun relationsComeTensestFirst() {
        val f = bundledForecast()
        val order = f.relationsOf(365).map { Tension.order(it.tension) }
        assertEquals(order.sorted(), order)
        assertEquals("Ukraine", f.relationsOf(365).first().other(365))
    }

    @Test fun anOddFileStillOpens() {
        val f = Forecast.parse("""{"countries": [{"id": 1, "name": "Somewhere"}], "pairs": {}}""")
        assertEquals("Somewhere", f.countries.single().name)
        assertEquals("Calm", f.countries.single().status)
        assertTrue(f.pairs.isEmpty())
    }

    @Test fun wordsMatchSage() {
        assertEquals("extremely unlikely", Words.chance(0.004))
        assertEquals("under 1%", Words.pct(0.004))
        assertEquals("a real possibility", Words.chance(0.25))
        assertEquals("almost certain", Words.chance(0.97))
        assertEquals("over 99%", Words.pct(0.996))
        assertEquals("<1%", Words.pctShort(0.004))
        assertEquals(">99%", Words.pctShort(0.996))
        assertEquals("Nov", Words.monthShort("2026-11"))
        assertEquals("November", Words.month("2026-11"))
        assertEquals("September 2026", Words.monthYear("2026-09"))
        assertEquals("28 October", Words.day("2026-10-28T18:07:57Z"))
        assertEquals("12 October", Words.day("2026-10-12"))
    }
}

class WatchlistTest {
    private fun country(id: Int, name: String, fighting: Double, alert: Boolean = false, state: Int = 0) = Country(
        id, name, "Europe", LastMonth(state, 0, 0, 0),
        Alert(alert, since = "2026-10-12", kind = "aimed", timesUsual = 4.7),
        mapOf("fighting" to Outlook(listOf(fighting, fighting, fighting), listOf(0.01, 0.01, 0.01), emptyList())),
        "",
    )

    private fun forecast(countries: List<Country>, pairs: List<Relation> = emptyList(), world: World? = null) = Forecast(
        "2026-10-28T00:00:00Z", "2026-09", listOf("2026-10", "2026-11", "2026-12"),
        mapOf("fighting" to Question("Deadly fighting", "")), emptyList(), null, null, countries, pairs, world = world,
    )

    private fun relation(tension: String) =
        Relation(200, 365, listOf("United Kingdom", "Russia"), tension, 0.003, 0.001, null, "")

    private fun seen(f: Forecast, vararg ids: Int) = Watchlist.snapshot(f, ids.toList())

    @Test fun nothingMovedNothingToSay() {
        val f = forecast(listOf(country(1, "Mali", 0.15)))
        assertTrue(Watchlist.changes(seen(f, 1), f, 1).isEmpty())
        assertTrue(Watchlist.changes(null, f, 1).isEmpty())
    }

    @Test fun aChanceMovingEitherWayIsNews() {
        val before = seen(forecast(listOf(country(1, "Mali", 0.15))), 1)
        val up = Watchlist.changes(before, forecast(listOf(country(1, "Mali", 0.45))), 1).single()
        assertEquals(Change("Deadly fighting in Nov: 15% → 45%", worse = true), up)
        val down = Watchlist.changes(before, forecast(listOf(country(1, "Mali", 0.05))), 1).single()
        assertEquals(Change("Deadly fighting in Nov: 15% → 5%", worse = false), down)
    }

    @Test fun smallMovesAndTinyChancesAreNot() {
        val tiny = seen(forecast(listOf(country(1, "Peru", 0.001))), 1)
        assertTrue(Watchlist.changes(tiny, forecast(listOf(country(1, "Peru", 0.012))), 1).isEmpty())
        val steady = seen(forecast(listOf(country(1, "Chad", 0.30))), 1)
        assertTrue(Watchlist.changes(steady, forecast(listOf(country(1, "Chad", 0.31))), 1).isEmpty())
    }

    @Test fun alertsAndStatusStartingAndEnding() {
        val calm = seen(forecast(listOf(country(1, "Yemen", 0.3))), 1)
        val texts = Watchlist.changes(calm, forecast(listOf(country(1, "Yemen", 0.3, alert = true, state = 40))), 1).map { it.text }
        assertEquals(listOf("New build-up alert", "Fighting last month, was calm"), texts)
        val alerted = seen(forecast(listOf(country(1, "Yemen", 0.3, alert = true))), 1)
        assertEquals(listOf(Change("Build-up alert over", worse = false)),
            Watchlist.changes(alerted, forecast(listOf(country(1, "Yemen", 0.3))), 1))
    }

    @Test fun tensionRisingToHighIsNewsButAboveUsualIsNot() {
        val uk = country(200, "United Kingdom", 0.001)
        val before = seen(forecast(listOf(uk), listOf(relation("above usual"))), 200)
        assertEquals(listOf("Tension with Russia: above usual → high"),
            Watchlist.changes(before, forecast(listOf(uk), listOf(relation("high"))), 200).map { it.text })
        val usual = seen(forecast(listOf(uk), listOf(relation("usual"))), 200)
        assertTrue(Watchlist.changes(usual, forecast(listOf(uk), listOf(relation("above usual"))), 200).isEmpty())
    }

    @Test fun theGlobalThreatLevelMovingIsNews() {
        fun level(rank: Int, name: String) = World(name, rank, 0.5, 15.0, 1.0, "Global threat level: $name.")
        val before = seen(forecast(emptyList(), world = level(2, "Elevated")))
        assertEquals(Change("Global threat level: Elevated → High", worse = true),
            Watchlist.world(before, forecast(emptyList(), world = level(3, "High"))))
        assertEquals(null, Watchlist.world(before, forecast(emptyList(), world = level(2, "Elevated"))))
    }

    @Test fun whatYouSawIsKept() {
        val f = bundledForecast()
        val s = Watchlist.snapshot(f, listOf(200, 678, 365))
        assertEquals(s, Seen.parse(s.toJson()))
        assertEquals(null, Seen.parse("not json"))
    }
}

class SearchTest {
    @Test fun findsCountriesByNameAndByOtherNames() {
        val f = bundledForecast()
        assertEquals("United Kingdom", Search.find(f, "uk").first().name)
        assertEquals("United States of America", Search.find(f, "USA").first().name)
        assertEquals("DR Congo", Search.find(f, "drc").first().name)
        assertEquals("Ivory Coast", Search.find(f, "côte").first().name)
        assertEquals(listOf("Congo", "DR Congo"), Search.find(f, "congo").map { it.name })
        assertTrue(Search.find(f, "zzzz").isEmpty())
    }
}

class ShareTest {
    @Test fun sharedTextSaysItPlainlyAndLinksToTheWebsite() {
        val f = bundledForecast()
        val yemen = Share.countryText(f, f.place(678)!!)
        assertTrue(yemen, yemen.startsWith("SAGE forecast for Yemen: deadly fighting in November"))
        assertTrue(yemen.contains("Build-up alert on."))
        assertTrue(yemen.endsWith("https://wysesage.xyz/#/country/678"))
        val pair = Share.pairText(f.relation(365, 369)!!)
        assertTrue(pair, pair.startsWith("SAGE: tension between Russia and Ukraine is at war."))
        assertTrue(pair.endsWith("https://wysesage.xyz/#/pair/365-369"))
    }
}
