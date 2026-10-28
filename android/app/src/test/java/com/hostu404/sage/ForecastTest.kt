package com.hostu404.sage

import com.hostu404.sage.data.Alert
import com.hostu404.sage.data.Changes
import com.hostu404.sage.data.Country
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.LastMonth
import com.hostu404.sage.data.Outlook
import com.hostu404.sage.data.Question
import com.hostu404.sage.data.Relation
import com.hostu404.sage.data.Tension
import com.hostu404.sage.data.WhereYouAre
import com.hostu404.sage.data.Words
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
        assertEquals("November", Words.month("2026-11"))
        assertEquals("September 2026", Words.monthYear("2026-09"))
        assertEquals("28 October", Words.day("2026-10-28T18:07:57Z"))
        assertEquals("12 October", Words.day("2026-10-12"))
    }
}

class ChangesTest {
    private fun country(id: Int, name: String, fighting: Double, alert: Boolean = false) = Country(
        id, name, "Europe", LastMonth(0, 0, 0, 0),
        Alert(alert, since = "2026-10-12", kind = "aimed", timesUsual = 4.7),
        mapOf("fighting" to Outlook(listOf(fighting, fighting, fighting), listOf(0.01, 0.01, 0.01), emptyList())),
        "",
    )

    private fun forecast(countries: List<Country>, pairs: List<Relation> = emptyList()) = Forecast(
        "2026-10-28T00:00:00Z", "2026-09", listOf("2026-10", "2026-11", "2026-12"),
        mapOf("fighting" to Question("Deadly fighting", "")), emptyList(), null, null, countries, pairs,
    )

    private fun relation(tension: String) =
        Relation(200, 365, listOf("United Kingdom", "Russia"), tension, 0.003, 0.001, null, "")

    @Test fun anAlertStartingIsNews() {
        val notices = Changes.between(forecast(listOf(country(1, "Yemen", 0.3))),
            forecast(listOf(country(1, "Yemen", 0.3, alert = true))), setOf(1))
        assertEquals(listOf("Build-up alert: Yemen"), notices.map { it.title })
        assertTrue(notices.single().text.contains("about 5 times usual"))
    }

    @Test fun aChanceRisingToANewWordIsNews() {
        val notices = Changes.between(forecast(listOf(country(1, "Mali", 0.15))),
            forecast(listOf(country(1, "Mali", 0.45))), setOf(1))
        assertEquals(1, notices.size)
        assertEquals("Deadly fighting in November is now about as likely as not (45%), up from unlikely (15%).",
            notices.single().text)
    }

    @Test fun smallChangesCalmerOutlooksAndOtherCountriesAreNot() {
        val quiet = Changes.between(forecast(listOf(country(1, "Peru", 0.001), country(2, "Chad", 0.6))),
            forecast(listOf(country(1, "Peru", 0.03), country(2, "Chad", 0.3))), setOf(1, 2))
        assertTrue(quiet.isEmpty())
        val notFollowed = Changes.between(forecast(listOf(country(1, "Mali", 0.15))),
            forecast(listOf(country(1, "Mali", 0.9))), setOf(2))
        assertTrue(notFollowed.isEmpty())
    }

    @Test fun tensionRisingToHighIsNewsButAboveUsualIsNot() {
        val uk = country(200, "United Kingdom", 0.001)
        val high = Changes.between(forecast(listOf(uk), listOf(relation("above usual"))),
            forecast(listOf(uk), listOf(relation("high"))), setOf(200))
        assertEquals(listOf("United Kingdom and Russia: tension high"), high.map { it.title })
        val mild = Changes.between(forecast(listOf(uk), listOf(relation("usual"))),
            forecast(listOf(uk), listOf(relation("above usual"))), setOf(200))
        assertTrue(mild.isEmpty())
    }
}
