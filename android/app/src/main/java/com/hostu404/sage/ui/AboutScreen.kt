package com.hostu404.sage.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hostu404.sage.SageModel
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Words
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(model: SageModel, f: Forecast) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About SAGE") },
                navigationIcon = {
                    IconButton(onClick = { model.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(),
            bottom = pad.calculateBottomPadding() + 8.dp)) {
            item {
                Paragraph("SAGE forecasts war and deadly violence, country by country, a few months ahead. It " +
                    "looks at how much fighting there's been, what the news says each side is doing, and what " +
                    "happened in similar situations since 1989. It's free, with no ads and no accounts.")
            }
            item {
                SectionTitle("How it works")
                Bullets(listOf(
                    "Each month, SAGE reads the latest count of people killed in organised violence (UCDP) and " +
                        "forecasts the next three months for every country where there's been any since 1989.",
                    "Each day, it reads the world's news (GDELT): who is threatening, mobilising or attacking whom, " +
                        "compared with what's usual for that country.",
                    "When several warning signs are far above usual at once, it raises a build-up alert.",
                    "Between countries, it also takes into account whether they share a border and how often " +
                        "they've clashed in the past (Correlates of War).",
                ))
            }
            item { Accuracy(f) }
            item {
                SectionTitle("What it can't do")
                Bullets(listOf(
                    "It can't see surprises that don't show up in the news first.",
                    "Most build-ups never turn into war, so even serious warning signs often come with small " +
                        "percentages. A small chance isn't no chance.",
                    "It forecasts countries, not towns or people, and it isn't a warning service: for travel and " +
                        "safety, follow official advice.",
                ))
            }
            item {
                SectionTitle("Privacy")
                Paragraph("SAGE asks for no permissions, has no accounts and collects nothing about you. When you " +
                    "open it, it downloads one public file with the forecasts, and shows what got worse in your " +
                    "countries since you last looked.")
                Paragraph("To show the country you're in, it asks Cloudflare which country your internet " +
                    "connection is in. Like any website you visit, Cloudflare sees your IP address; SAGE keeps " +
                    "only the country, on your phone, with the countries you follow.")
            }
            item {
                SectionTitle("Where the numbers come from")
                Bullets(listOf(
                    "Uppsala Conflict Data Program (UCDP): Georeferenced Event Dataset and Candidate Events, CC BY 4.0.",
                    "The GDELT Project, gdeltproject.org.",
                    "Correlates of War Project: Dyadic Militarized Interstate Disputes and Direct Contiguity.",
                    "ViEWS (Uppsala University and PRIO), used to check SAGE against.",
                ))
                Text("Forecast made ${Words.day(f.generated)}, from conflict data to ${Words.monthYear(f.dataThrough)}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp))
            }
            item {
                SectionTitle("Licence")
                Paragraph("SAGE is free to use and share with credit. Its code is open source under the Apache " +
                    "License 2.0, and its forecasts are licensed under Creative Commons Attribution 4.0: if you " +
                    "copy or share them, credit \"SAGE (github.com/Hostu404/SAGE)\".")
            }
            item { Disclaimer() }
        }
    }
}

/** The track record from the forecast file, in plain words. */
@Composable
private fun Accuracy(f: Forecast) {
    val record = f.record ?: return
    val fighting = record.scores["fighting_1"] ?: return
    SectionTitle("How accurate it is")
    val lines = mutableListOf(
        "SAGE was replayed month by month from ${Words.monthYear(record.from)} to ${Words.monthYear(record.to)}, " +
            "using only what was known at the time, and checked against what happened.",
        "Take a country that went on to have deadly fighting the next month and one that didn't: SAGE rated " +
            "the first one higher about ${(fighting.auc * 100).roundToInt()} times in 100.",
        "Its errors were ${(fighting.betterThanLastMonth * 100).roundToInt()}% smaller than assuming next month " +
            "will be like this one, which is the forecast to beat.",
    )
    f.alertRecord?.questions?.get("fighting")?.takeIf { it.alerts > 0 }?.let {
        lines += "After past build-up alerts, deadly fighting followed within three months in ${it.followed} of " +
            "${it.alerts} cases (${(100.0 * it.followed / it.alerts).roundToInt()}%), against " +
            "${Words.pct(it.usual)} for an ordinary country."
    }
    Bullets(lines)
}

@Composable
private fun Paragraph(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
