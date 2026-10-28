package com.hostu404.sage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hostu404.sage.SageModel
import com.hostu404.sage.data.Alert
import com.hostu404.sage.data.Country
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Outlook
import com.hostu404.sage.data.Words

private const val RELATIONS_SHOWN = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(model: SageModel, f: Forecast, id: Int) {
    val place = f.place(id)
    val following = id in model.followed
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "Not found") },
                navigationIcon = {
                    IconButton(onClick = { model.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (place != null) IconButton(onClick = { model.toggleFollow(id) }) {
                        Icon(if (following) Icons.Filled.Star else Icons.Filled.StarBorder,
                            if (following) "Stop following" else "Follow on the home screen",
                            tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { pad ->
        var allRelations by rememberSaveable(id) { mutableStateOf(false) }
        val relations = f.relationsOf(id)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 8.dp),
        ) {
            if (place == null) {
                item { Text("SAGE has nothing on this country.", style = MaterialTheme.typography.bodyLarge) }
                return@LazyColumn
            }
            val c = place.country
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(c?.status ?: "Relations only", statusColor(c))
                    if (c != null) Text("  ${c.region}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (c == null) item {
                Text(
                    "There's been no organised violence recorded in ${place.name} since 1989, so SAGE doesn't forecast " +
                        "fighting inside it. It does follow ${place.name}'s relations with other countries, below.",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                if (c.alert.on) item { AlertCard(c, f) }
                item {
                    SectionTitle("Where things stand")
                    // The alert card above already says what the summary's last "Warning:" sentence does.
                    val summary = if (c.alert.on) c.summary.substringBefore(" Warning: ") else c.summary
                    SageCard { Text(summary, style = MaterialTheme.typography.bodyLarge) }
                }
                item { SectionTitle("The chances") }
                Forecast.QUESTION_KEYS.forEach { key ->
                    val outlook = c.outlooks[key] ?: return@forEach
                    item(key) { OutlookCard(f, key, outlook, Modifier.padding(bottom = 10.dp)) }
                }
            }
            if (relations.isNotEmpty()) item {
                SectionTitle("Relations with other countries")
                SageCard {
                    val shown = if (allRelations) relations else relations.take(RELATIONS_SHOWN)
                    shown.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        RelationRow(r, r.other(id)) { model.showRelation(r) }
                    }
                    if (relations.size > RELATIONS_SHOWN) {
                        TextButton(onClick = { allRelations = !allRelations }, modifier = Modifier.align(Alignment.End)) {
                            Text(if (allRelations) "Show fewer" else "Show all ${relations.size}")
                        }
                    }
                }
            }
            item { Disclaimer() }
        }
    }
}

@Composable
private fun AlertCard(c: Country, f: Forecast) {
    val a: Alert = c.alert
    val who = if (a.kind == "aimed") "other countries' forces massing against ${c.name}"
    else "${c.name}'s forces mobilising and threats of force"
    val since = a.since?.let { " since ${Words.day(it)}" } ?: ""
    val times = a.timesUsual?.let { ", up to about ${"%.0f".format(it)} times usual" } ?: ""
    val record = f.alertRecord?.questions?.get(if (c.lastMonth.state < 25) "fighting" else "major_war")
    val past = record?.takeIf { it.alerts > 0 }?.let {
        val what = if (c.lastMonth.state < 25) "deadly fighting" else "full-scale war"
        " After past alerts like this, $what followed within three months in ${it.followed} of ${it.alerts} cases."
    } ?: ""
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text("Build-up alert", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text("News of $who has been far above usual$since$times.$past An alert is a warning sign, not a forecast.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** One question: next month's chance in words and numbers, the three months, the usual, and why. */
@Composable
private fun OutlookCard(f: Forecast, key: String, o: Outlook, modifier: Modifier = Modifier) {
    val q = f.questions[key]
    val next = o.chance.getOrNull(Forecast.NEXT) ?: return
    SageCard(modifier) {
        Text(q?.title ?: key, style = MaterialTheme.typography.titleMedium)
        q?.text?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val month = f.months.getOrNull(Forecast.NEXT)?.let { Words.month(it) } ?: "Next month"
        ChanceLine(next, Modifier.padding(top = 12.dp), label = month)
        Text(
            f.months.zip(o.chance).joinToString("   ") { (m, p) -> "${Words.month(m).take(3)} ${Words.pct(p)}" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        o.usual.getOrNull(Forecast.NEXT)?.let {
            Text("Usual for a country in its situation: ${Words.pct(it)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (o.why.isNotEmpty()) {
            Text("Why", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Bullets(o.why)
        }
    }
}
