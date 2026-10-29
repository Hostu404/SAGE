package com.hostu404.sage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostu404.sage.SageModel
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.REPO_URL
import com.hostu404.sage.data.Words
import java.util.Locale
import kotlin.math.roundToInt

/** The same as the website's About page, plus what the app itself does. */
@Composable
fun AboutScreen(model: SageModel, f: Forecast) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(Sage.bg)) {
        TopBar(title = "About SAGE", onBack = { model.back() })
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Panel("About SAGE") {
                    Body("SAGE forecasts war and deadly violence, country by country, a few months ahead. It looks at how much " +
                        "fighting there's been, what the news says each side is doing, and what happened in similar situations " +
                        "since 1989. It's free, with no ads and no accounts.", Modifier.padding(14.dp))
                    Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runCatching { uri.openUri(REPO_URL) } }) { Text("VIEW SOURCE", style = label(11.sp), color = Sage.text) }
                        OutlinedButton(onClick = { runCatching { uri.openUri(Share.SITE) } }) { Text("WYSESAGE.XYZ", style = label(11.sp), color = Sage.text) }
                    }
                }
            }
            item {
                Panel("How it works") {
                    Bullets(listOf(
                        "Each month, SAGE reads the latest count of people killed in organised violence (UCDP), which runs about a " +
                            "month behind, and forecasts the three months after it for every country where there's been any since 1989. " +
                            "The first of those months is usually already under way.",
                        "Each day, it reads the world's news (GDELT): who is threatening, mobilising or attacking whom, compared with " +
                            "what's usual for that country.",
                        "When several warning signs are far above usual at once, it raises a build-up alert.",
                        "Between countries, it also takes into account whether they share a border and how often they've clashed in " +
                            "the past (Correlates of War).",
                        "The global threat level compares how many countries SAGE expects to see deadly fighting and full-scale war next " +
                            "month with every month since 1989.",
                    ), Modifier.padding(14.dp))
                }
            }
            accuracy(f)?.let { lines -> item { Panel("How accurate it is") { Bullets(lines, Modifier.padding(14.dp)) } } }
            item {
                Panel("What it can't do") {
                    Bullets(listOf(
                        "It can't see surprises that don't show up in the news first.",
                        "Most build-ups never turn into war, so even serious warning signs often come with small percentages. A " +
                            "small chance isn't no chance.",
                        "Between countries, chances under about a third run low: since 2018, when SAGE said about 5% for deadly " +
                            "clashes within six months, they followed about 7% of the time, and at about 20%, about 29%. Higher " +
                            "chances have been close.",
                        "It forecasts countries, not towns or people, and it isn't a warning service: for travel and safety, follow " +
                            "official advice.",
                    ), Modifier.padding(14.dp))
                }
            }
            item {
                Panel("In the app") {
                    Bullets(listOf(
                        "Star countries to follow them: they come first, with what moved since you last looked.",
                        "Share any country or pair as a picture, with a link to its page on wysesage.xyz.",
                        "The SAGE widget puts the global threat level and where you are on your home screen, and keeps them up " +
                            "to date a few times a day.",
                        "It works offline, from the last forecast it downloaded.",
                    ), Modifier.padding(14.dp))
                }
            }
            item {
                Panel("Privacy") {
                    Body("No accounts, no ads, nothing collected. SAGE downloads one public file with the forecasts, and asks " +
                        "Cloudflare which country your connection is in; only the country is kept, on your phone. It needs no " +
                        "permission you're asked for: just internet access and, for the widget's updates, whether you're online.",
                        Modifier.padding(14.dp))
                }
            }
            item {
                Panel("Where the numbers come from") {
                    Bullets(listOf(
                        "Uppsala Conflict Data Program (UCDP): Georeferenced Event Dataset and Candidate Events, CC BY 4.0.",
                        "The GDELT Project.",
                        "Correlates of War Project: Dyadic Militarized Interstate Disputes and Direct Contiguity.",
                        "ViEWS (Uppsala University and PRIO), used to check SAGE against.",
                        "Map: Natural Earth (public domain).",
                    ), Modifier.padding(14.dp))
                }
            }
            item {
                Panel("Licence") {
                    Body("SAGE is free to use and share with credit. Its code is open source under the Apache License 2.0, and its " +
                        "forecasts are licensed under Creative Commons Attribution 4.0: if you copy or share them, credit " +
                        "\"SAGE (github.com/Hostu404/SAGE)\".", Modifier.padding(14.dp))
                }
            }
            item { Footer(made(f)) }
        }
    }
}

/** The track record from the forecast file, in plain words; "_2" is next month. */
private fun accuracy(f: Forecast): List<String>? {
    val record = f.record ?: return null
    val fighting = record.scores["fighting_2"] ?: record.scores["fighting_1"] ?: return null
    val lines = mutableListOf(
        "SAGE was replayed month by month from ${Words.monthYear(record.from)} to ${Words.monthYear(record.to)}, using only " +
            "what was known at the time, and checked against what happened.",
        "Shown two countries, one where deadly fighting followed the next month and one where it didn't, SAGE gave the higher " +
            "chance to the right one ${"%.1f".format(Locale.UK, fighting.auc * 100).removeSuffix(".0")} times in 100.",
        "Its errors were ${(fighting.betterThanLastMonth * 100).roundToInt()}% smaller than assuming next month will be like " +
            "this one, which is the forecast to beat.",
    )
    f.alertRecord?.questions?.get("fighting")?.takeIf { it.alerts > 0 }?.let {
        lines += "After past build-up alerts, deadly fighting followed within three months in ${it.followed} of ${it.alerts} " +
            "cases (${(100.0 * it.followed / it.alerts).roundToInt()}%), against ${Words.pct(it.usual)} for an ordinary country."
    }
    return lines
}
