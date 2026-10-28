package com.hostu404.sage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Place
import com.hostu404.sage.data.Relation
import com.hostu404.sage.data.Tension
import com.hostu404.sage.data.WhereYouAre
import com.hostu404.sage.data.Words

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: SageModel, f: Forecast) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SAGE", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { model.open(Screen.Search) }) { Icon(Icons.Outlined.Search, "Find a country") }
                    IconButton(onClick = { model.open(Screen.About) }) { Icon(Icons.Outlined.Info, "About SAGE") }
                },
            )
        },
    ) { pad ->
        val here = model.here
        val herePlace = here?.let { f.placeForIso2(it) }
        val followed = f.places.filter { it.id in model.followed && it.id != herePlace?.id }
        val alerts = f.countries.filter { it.alert.on && it.id !in model.followed && it.id != herePlace?.id }
        val tense = f.pairs.filter { it.tension == "very high" || it.tension == "high" }
            .sortedWith(compareBy<Relation> { Tension.order(it.tension) }.thenByDescending { it.clash6 }).take(5)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 8.dp),
        ) {
            item { Freshness(model, f) }
            if (model.changes.isNotEmpty()) item {
                SectionTitle("Since you last looked")
                SageCard {
                    Bullets(model.changes.map { "${it.title}. ${it.text}" })
                    TextButton(onClick = model::dismissChanges, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
                }
            }
            if (here != null) item {
                SectionTitle("Where you are")
                if (herePlace != null) {
                    PlaceCard(herePlace, f) { model.open(Screen.Place(herePlace.id)) }
                } else {
                    SageCard {
                        Text(WhereYouAre.name(here), style = MaterialTheme.typography.titleMedium)
                        Text("No organised violence has been recorded here since 1989, and SAGE sees no tension " +
                            "with another country worth following.", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Text("Found from your internet connection. A VPN can make this another country.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
            item {
                SectionTitle("Where the world is leaning")
                SageCard { Bullets(f.summary) }
            }
            item { SectionTitle("Your countries") }
            if (followed.isEmpty()) item {
                Text("Find a country with the search button and tap its star to keep it here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(followed, key = { "f${it.id}" }) { place ->
                PlaceCard(place, f, Modifier.padding(bottom = 10.dp)) { model.open(Screen.Place(place.id)) }
            }
            if (alerts.isNotEmpty()) {
                item { SectionTitle("Build-up alerts") }
                items(alerts, key = { "a${it.id}" }) { c ->
                    PlaceCard(f.place(c.id) ?: Place(c.id, c.name, c), f, Modifier.padding(bottom = 10.dp)) {
                        model.open(Screen.Place(c.id))
                    }
                }
            }
            if (tense.isNotEmpty()) {
                item {
                    SectionTitle("Tensest between countries not fighting")
                    SageCard {
                        tense.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            RelationRow(r, r.title) { model.showRelation(r) }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { model.open(Screen.Search) }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Text("All countries")
                }
            }
            item { Disclaimer() }
        }
    }
}

@Composable
private fun Freshness(model: SageModel, f: Forecast) {
    val news = f.alertRecord?.checkedThrough?.takeIf { it.isNotEmpty() }?.let { " · news to ${Words.day(it)}" } ?: ""
    Text(
        "Conflict data to ${Words.monthYear(f.dataThrough)}$news" + if (model.refreshing) " · checking for updates…" else "",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A country at a glance: how things stand, the chance that matters most, and its tensest relation. */
@Composable
fun PlaceCard(place: Place, f: Forecast, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = place.country
    SageCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (c?.alert?.on == true) { AlertChip(); Spacer(Modifier.width(6.dp)) }
            Chip(c?.status ?: "Relations only", statusColor(c))
        }
        val month = f.months.getOrNull(Forecast.NEXT)
        if (c != null) {
            val (what, key) = when {
                c.lastMonth.state >= 1000 -> "Full-scale war continuing" to "major_war"
                c.lastMonth.state >= 25 -> "More deadly fighting" to "fighting"
                else -> "Deadly fighting" to "fighting"
            }
            c.outlooks[key]?.chance?.getOrNull(Forecast.NEXT)?.let {
                Text(chanceSentence(what, month, it), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        f.relationsOf(place.id).firstOrNull { it.tension != "usual" }?.let { r ->
            Text("Tension with ${r.other(place.id)}: ${r.tension}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** One relation in a list: who, how tense, and the chance of clashes. */
@Composable
fun RelationRow(r: Relation, name: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text("Deadly clashes in six months: ${Words.pct(r.clash6)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Chip(r.tension.replaceFirstChar { it.uppercase() }, tensionColor(r.tension))
    }
}
