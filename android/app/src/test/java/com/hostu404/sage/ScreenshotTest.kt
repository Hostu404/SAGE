package com.hostu404.sage

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performClick
import com.hostu404.sage.ui.Sage
import com.hostu404.sage.ui.ThreatDetail
import com.hostu404.sage.ui.ThreatPart
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Seen
import com.hostu404.sage.data.Watchlist
import com.hostu404.sage.data.WorldShapes
import com.hostu404.sage.ui.App
import com.hostu404.sage.ui.SageTheme
import com.hostu404.sage.ui.ShareCard
import com.hostu404.sage.widget.SageWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Renders each screen to app/build/outputs/roborazzi, from the forecast the app ships with. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2200dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = "build/outputs/roborazzi"
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun model(vararg screens: Screen, here: String = "GB", followed: List<Int> = emptyList(),
                      seen: ((Forecast) -> Seen)? = null): SageModel {
        WorldShapes.get(app) // read the map first, so it's drawn in the first frame
        val m = SageModel(app, autoRefresh = false)
        followed.forEach { m.toggleFollow(it) }
        val f = bundledForecast()
        m.show(f, here, seen?.invoke(f))
        screens.forEach { m.open(it) }
        compose.setContent { SageTheme { App(m) } }
        ShadowLooper.idleMainLooper()
        compose.waitForIdle()
        return m
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("$out/$name.png")

    private fun save(bitmap: Bitmap, name: String) {
        File(out).mkdirs()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() {
        val m = model()
        shot("01-home")
        m.homeList.requestScrollToItem(4)
        compose.waitForIdle()
        shot("01b-home-lower")
        m.homeList.requestScrollToItem(8)
        compose.waitForIdle()
        shot("01c-home-every-country")
    }

    @Test fun watchlistWithChanges() {
        model(here = "GB", followed = listOf(678, 365)) { f ->
            // As if you'd last looked a while ago, when things were calmer.
            val s = Watchlist.snapshot(f, listOf(200, 365, 678))
            s.copy(worldLevel = "Elevated", worldRank = 2, countries = s.countries + mapOf(
                678 to s.countries.getValue(678).copy(fighting = 0.71, alert = false),
                200 to s.countries.getValue(200).copy(tensions = s.countries.getValue(200).tensions + (666 to "above usual")),
            ))
        }
        compose.onNodeWithText("New build-up alert").assertExists()
        compose.onNodeWithText("Global threat level: Elevated → High").assertExists()
        shot("02-watchlist-changes")
    }

    @Test fun homeSomewhereSageHasNothingOn() { model(here = "IS"); shot("03-home-iceland") }

    @Test fun yemenWithAlert() { model(Screen.Place(678)); shot("04-yemen") }

    @Test fun unitedKingdom() { model(Screen.Place(200)); shot("05-united-kingdom") }

    @Test fun estoniaRelationsOnly() {
        val f = bundledForecast()
        model(Screen.Place(f.places.first { it.name == "Estonia" }.id))
        shot("06-estonia")
    }

    @Test fun ukAndRussia() { model(Screen.Pair(200, 365)); shot("07-uk-russia") }

    @Test fun russiaAndUkraine() { model(Screen.Pair(365, 369)); shot("08-russia-ukraine") }

    @Test fun search() {
        model(Screen.Search)
        compose.onNode(hasSetTextAction()).performTextInput("cote")
        compose.waitForIdle()
        compose.onNodeWithText("Ivory Coast").assertExists()
        shot("09-search")
    }

    @Test fun about() { model(Screen.About); shot("10-about") }

    /** Each part of the global threat level, opened: what it's made of. */
    @Test fun threatDetails() {
        val f = bundledForecast()
        var part by mutableStateOf(ThreatPart.FIGHTING)
        compose.setContent { SageTheme { Box(Modifier.background(Sage.panel)) { ThreatDetail(part, f.world!!, f, onOpen = {}) } } }
        listOf(ThreatPart.FIGHTING, ThreatPart.WAR, ThreatPart.HISTORY, ThreatPart.ALERTS, ThreatPart.LEVEL).forEachIndexed { i, p ->
            part = p
            compose.waitForIdle()
            shot("13${'a' + i}-threat-$p")
        }
    }

    @Test fun tappingAThreatNumberOpensWhatItsMadeOf() {
        model()
        compose.onNodeWithText("build-up alerts ›").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("An alert goes out", substring = true).assertExists()
    }

    @Test fun aCountryInTheDetailsOpensIt() {
        val f = bundledForecast()
        var opened: Int? = null
        compose.setContent { SageTheme { ThreatDetail(ThreatPart.ALERTS, f.world!!, f, onOpen = { opened = it }) } }
        compose.onNodeWithText("Yemen").performClick()
        assertEquals(678, opened)
    }

    @Test fun widget() {
        val f = bundledForecast()
        val density = app.resources.displayMetrics.density
        for ((name, here, compact) in listOf(Triple("11-widget", "GB", false), Triple("11b-widget-yemen", "YE", false),
            Triple("11c-widget-short", "YE", true))) {
            val view: View = SageWidget.views(app, f, here, emptySet(), compact).apply(app, FrameLayout(app))
            val w = (300 * density).toInt()
            val h = ((if (compact) 110f else SageWidget.FULL_HEIGHT) * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            save(bitmap, name)
        }
    }

    @Test fun shareCards() {
        val f = bundledForecast()
        save(ShareCard.country(app, f, f.place(678)!!), "12-share-yemen")
        save(ShareCard.pair(app, f, f.relation(365, 369)!!), "12b-share-russia-ukraine")
    }

    @Test fun theMapKnowsEveryCountryAndWhereATapLands() {
        val world = WorldShapes.get(app)
        val f = bundledForecast()
        val missing = f.iso3.values.filter { it !in world.countries }
        assertTrue("no shape for $missing", missing.isEmpty())
        // A tap in the middle of France lands on France, and near Bahrain's dot on Bahrain.
        val france = world.countries.getValue("FRA").box
        assertEquals("FRA", world.hit(france.centerX(), france.centerY(), world.countries.keys, reach = 2f))
        val bahrain = world.countries.getValue("BHR").dot!!
        assertEquals("BHR", world.hit(bahrain.x + 0.5f, bahrain.y, setOf("BHR", "SAU", "QAT"), reach = 2f))
        // The relations map frames a country and its tense neighbours, not the whole world.
        val frame = world.frame(listOf("SAU", "YEM"))
        assertTrue(frame.width() < world.viewBox.width() / 2)
    }
}
