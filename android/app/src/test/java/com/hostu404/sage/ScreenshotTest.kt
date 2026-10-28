package com.hostu404.sage

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.hostu404.sage.ui.App
import com.hostu404.sage.ui.RelationDetails
import com.hostu404.sage.ui.SageTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** Renders each screen to app/build/outputs/roborazzi, from the forecast the app ships with. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1300dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = "build/outputs/roborazzi"

    private fun model(dark: Boolean = false, vararg screens: Screen, here: String = "GB"): SageModel {
        val m = SageModel(ApplicationProvider.getApplicationContext<Application>(), autoRefresh = false)
        m.show(bundledForecast(), here)
        screens.forEach { m.open(it) }
        compose.setContent { SageTheme(dark = dark) { App(m) } }
        ShadowLooper.idleMainLooper()
        compose.waitForIdle()
        return m
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("$out/$name.png")

    @Test fun home() { model(); shot("01-home") }

    @Test fun homeInFrance() { model(here = "FR"); shot("01b-home-france") }

    @Test fun homeSomewhereSageHasNothingOn() { model(here = "IS"); shot("01c-home-iceland") }

    @Test fun homeDark() { model(dark = true); shot("02-home-dark") }

    @Test fun unitedKingdom() { model(false, Screen.Place(200)); shot("03-united-kingdom") }

    @Test fun ukraine() { model(false, Screen.Place(369)); shot("04-ukraine") }

    @Test fun yemenWithAlert() { model(false, Screen.Place(678)); shot("05-yemen-alert") }

    @Test fun estoniaRelationsOnly() {
        val f = bundledForecast()
        model(false, Screen.Place(f.places.first { it.name == "Estonia" }.id))
        shot("06-estonia")
    }

    @Test fun ukAndRussia() {
        val r = bundledForecast().relationsOf(200).first { it.other(200) == "Russia" }
        compose.setContent {
            SageTheme { Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { RelationDetails(r, Modifier.padding(20.dp)) } }
        }
        compose.waitForIdle()
        shot("07-uk-russia")
    }

    @Test fun search() {
        model(false, Screen.Search)
        compose.onNodeWithText("Find a country").assertExists()
        shot("08-search")
    }

    @Test fun about() { model(false, Screen.About); shot("09-about") }
}
