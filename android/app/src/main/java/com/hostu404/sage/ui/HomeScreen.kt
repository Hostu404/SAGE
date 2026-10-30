package com.hostu404.sage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen
import com.hostu404.sage.data.Change
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Place
import com.hostu404.sage.data.Tension
import com.hostu404.sage.data.Watchlist
import com.hostu404.sage.data.WhereYouAre
import com.hostu404.sage.data.Words
import com.hostu404.sage.data.World
import kotlin.math.roundToInt

/** The overview: the website's dashboard, with your countries near the top. */
@Composable
fun HomeScreen(model: SageModel, f: Forecast) {
    Column(Modifier.fillMaxSize().background(Sage.bg)) {
        TopBar(actions = {
            IconButton(onClick = { model.open(Screen.Search) }) { Icon(Icons.Outlined.Search, "Find a country", tint = Sage.text) }
            IconButton(onClick = { model.open(Screen.About) }) { Icon(Icons.Outlined.Info, "About SAGE", tint = Sage.text) }
        })
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            state = model.homeList,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Freshness(model, f) }
            f.world?.let { w -> item { ThreatPanel(model, w, f) } }
            item { WatchlistPanel(model, f) }
            item { RiskMapPanel(model, f) }
            item { MostLikelyPanel(model, f) }
            item { AlertsPanel(model, f) }
            item { TensionPanel(model, f) }
            item { Panel("Summary") { Lines(f.summary.filterNot { it.startsWith("Global threat level") }) } }
            item { EveryCountryPanel(model, f) }
            item { Footer(made(f)) }
        }
    }
}

@Composable
private fun Freshness(model: SageModel, f: Forecast) {
    val news = f.alertRecord?.checkedThrough?.takeIf { it.isNotEmpty() }?.let { " · news to ${Words.day(it)}" } ?: ""
    Text(("Updated ${Words.day(f.generated)} · data to ${Words.monthYear(f.dataThrough)}$news" +
        if (model.refreshing) " · checking for a newer one" else "").uppercase(),
        style = label(10.5.sp, FontWeight.Normal), color = Sage.faint)
}

/** The level and the four numbers behind it; tapping any of them opens what it's made of. */
@Composable
private fun ThreatPanel(model: SageModel, w: World, f: Forecast) {
    val month = w.month?.let(Words::month) ?: nextMonth(f)
    val last = w.lastMonth?.let(Words::month)
    val alerts = f.countries.count { it.alert.on }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Panel("Global threat level", right = { Note("For ${w.month?.let(Words::monthYear) ?: month}") }) {
        ThreatBlock(w, Modifier.clickable { open = ThreatPart.LEVEL }.padding(14.dp))
        HorizontalDivider(color = Sage.line)
        StatGrid(listOf(
            StatItem("${w.expectedFighting.roundToInt()}", "countries expected to see deadly fighting in $month" +
                (if (w.lastFighting != null && last != null) ", against ${w.lastFighting} in $last" else "")) { open = ThreatPart.FIGHTING },
            StatItem("${w.expectedWars.roundToInt()}", "expected to be at full-scale war" +
                (if (w.lastWars != null && last != null) ", against ${w.lastWars} in $last" else "")) { open = ThreatPart.WAR },
            StatItem("${(w.score * 100).roundToInt()}%", "of months since 1989 were calmer") { open = ThreatPart.HISTORY },
            StatItem("$alerts", if (alerts == 1) "build-up alert" else "build-up alerts", if (alerts > 0) Sage.alert else Sage.text) {
                open = ThreatPart.ALERTS
            },
        ))
    }
    open?.let { part ->
        ThreatSheet(part, w, f, onOpen = { id -> open = null; model.open(Screen.Place(id)) }, onDismiss = { open = null })
    }
}

/** Where you are and the countries you follow, each with what moved since you last looked. */
@Composable
private fun WatchlistPanel(model: SageModel, f: Forecast) {
    val ids = model.watched(f)
    val hereId = model.here?.let { f.placeForIso2(it)?.id }
    val world = Watchlist.world(model.seen, f)
    val changes = ids.associateWith { Watchlist.changes(model.seen, f, it) }
    val anything = world != null || changes.values.any { it.isNotEmpty() }
    Panel("Your countries", right = {
        if (anything) TextButton(onClick = model::markSeen) { Text("SEEN", style = label(11.sp), color = Sage.text) }
        else Note("Since you last looked")
    }) {
        world?.let { ChangeLine(it, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)); HorizontalDivider(color = Sage.line) }
        val unknownHere = model.here?.takeIf { hereId == null }
        if (unknownHere != null) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Name(WhereYouAre.name(unknownHere), weight = FontWeight.SemiBold)
                Text("No organised violence has been recorded here since 1989, and SAGE sees no tension with another country worth following.",
                    style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.padding(top = 3.dp))
            }
            if (ids.isNotEmpty()) HorizontalDivider(color = Sage.line)
        }
        ids.forEachIndexed { i, id ->
            val place = f.place(id) ?: return@forEachIndexed
            if (i > 0) HorizontalDivider(color = Sage.line)
            WatchRow(place, id == hereId, changes[id].orEmpty()) { model.open(Screen.Place(id)) }
        }
        if (model.followed.isEmpty()) {
            if (ids.isNotEmpty() || unknownHere != null) HorizontalDivider(color = Sage.line)
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Star a country to follow it here.", style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.open(Screen.Search) }) { Text("FIND A COUNTRY", style = label(11.sp), color = Sage.text) }
            }
        }
    }
}

@Composable
private fun WatchRow(place: Place, isHere: Boolean, changes: List<Change>, onClick: () -> Unit) {
    val c = place.country
    val p = chanceOf(c, "fighting")
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (isHere) Text("YOU'RE HERE", style = label(9.5.sp), color = Sage.faint, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Name(place.name, Modifier.weight(1f, fill = false), weight = FontWeight.SemiBold)
                    if (c?.alert?.on == true) Flag()
                }
            }
            StatusTag(c, Modifier.padding(start = 10.dp))
            Pct(p, Modifier.width(44.dp))
        }
        if (p != null) MBar(p, Modifier.fillMaxWidth().padding(top = 8.dp))
        changes.forEach { ChangeLine(it, Modifier.padding(top = 7.dp)) }
    }
}

/** "▲ Deadly fighting in Nov: 12% → 18%". */
@Composable
fun ChangeLine(c: Change, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(if (c.worse) "▲" else "▼", style = mono(11.sp), color = if (c.worse) Sage.alert else Sage.muted)
        Text(c.text, style = MaterialTheme.typography.bodySmall, color = Sage.text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RiskMapPanel(model: SageModel, f: Forecast) {
    val key = model.measure
    val what = MEASURES.getValue(key)
    var picked by remember { mutableStateOf<Place?>(null) }
    Panel("$what in ${nextMonth(f)}", right = {
        Toggle(listOf("fighting" to "Fighting", "major_war" to "War"), key) { model.measure = it }
    }) {
        WorldMap(f, fill = { mapColor(chanceOf(it?.country, key)) },
            description = "World map of each country's chance of ${what.lowercase()} in ${nextMonth(f)}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            picked = setOfNotNull(picked?.id), onTap = { picked = it })
        val pick = picked
        if (pick != null) {
            val p = chanceOf(pick.country, key)
            MapPick(pick, if (p == null) "No forecast of its own" else "$what: ${Words.pct(p)}", "Open ›") { model.open(Screen.Place(pick.id)) }
        }
        Legend(RISK_LEGEND)
    }
}

@Composable
private fun MostLikelyPanel(model: SageModel, f: Forecast) {
    val key = model.measure
    val ranked = f.countries.filter { chanceOf(it, key) != null }.sortedByDescending { chanceOf(it, key) }.take(10)
    Panel("Most likely: ${MEASURES.getValue(key).lowercase()}", right = { Note(nextMonth(f)) }) {
        ranked.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = Sage.line)
            val p = chanceOf(c, key) ?: 0.0
            ListRow({ model.open(Screen.Place(c.id)) }) {
                Text("${i + 1}", style = mono(12.sp, FontWeight.Normal), color = Sage.faint, modifier = Modifier.width(20.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Name(c.name, Modifier.weight(1f, fill = false))
                    if (c.alert.on) Flag()
                }
                MBar(p, Modifier.width(56.dp))
                Pct(p, Modifier.width(44.dp))
            }
        }
    }
}

@Composable
private fun AlertsPanel(model: SageModel, f: Forecast) {
    val alerts = f.countries.filter { it.alert.on }
    Panel("Build-up alerts", right = { if (alerts.isNotEmpty()) Note("${alerts.size}") }) {
        if (alerts.isEmpty()) Body("No build-up alerts right now.", Modifier.padding(14.dp), Sage.muted)
        alerts.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = Sage.line)
            Column(Modifier.fillMaxWidth().clickable { model.open(Screen.Place(c.id)) }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Name(c.name, weight = FontWeight.SemiBold)
                    StatusTag(c)
                }
                Text(alertShort(c), style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun TensionPanel(model: SageModel, f: Forecast) {
    val tense = f.pairs.filter { it.tension == "very high" || it.tension == "high" }
        .sortedWith(compareBy<com.hostu404.sage.data.Relation> { Tension.order(it.tension) }.thenByDescending { it.clash6 }).take(8)
    Panel("Tensest relations", right = { Note("Next six months") }) {
        if (tense.isEmpty()) {
            Body("No relations between countries are unusually tense right now.", Modifier.padding(14.dp), Sage.muted)
            return@Panel
        }
        RowsHead("Countries", "Tension", "Clashes")
        tense.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Sage.line)
            ListRow({ model.open(Screen.Pair(r.a, r.b)) }) {
                Name("${r.names[0]} – ${r.names[1]}", Modifier.weight(1f))
                Tag(r.tension, tensionColor(r.tension))
                Pct(r.clash6, Modifier.width(44.dp))
            }
        }
    }
}

/** Every country SAGE has, sortable by name or by either chance. */
@Composable
private fun EveryCountryPanel(model: SageModel, f: Forecast) {
    var by by rememberSaveable { mutableStateOf("name") }
    var desc by rememberSaveable { mutableStateOf(false) }
    var all by rememberSaveable { mutableStateOf(false) }
    val value = { p: Place -> if (by == "name") 0.0 else chanceOf(p.country, by) ?: -1.0 }
    val rows = f.places.sortedWith { x, y ->
        val v = (if (desc) -1 else 1) * value(x).compareTo(value(y))
        if (v != 0) v else (if (by == "name" && desc) -1 else 1) * x.name.compareTo(y.name)
    }
    Panel("Every country", right = { Note("Chance in ${nextMonth(f)}") }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val sort = { key: String -> desc = if (by == key) !desc else key != "name"; by = key }
            SortHead("Country", by == "name", desc, Modifier.weight(1f), TextAlign.Start) { sort("name") }
            SortHead("Fighting", by == "fighting", desc, Modifier.width(76.dp), TextAlign.End) { sort("fighting") }
            SortHead("War", by == "major_war", desc, Modifier.width(64.dp), TextAlign.End) { sort("major_war") }
        }
        HorizontalDivider(color = Sage.lineStrong)
        (if (all) rows else rows.take(SHOWN)).forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = Sage.line)
            ListRow({ model.open(Screen.Place(p.id)) }) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Name(p.name, Modifier.weight(1f, fill = false), weight = FontWeight.SemiBold)
                        if (p.country?.alert?.on == true) Flag()
                    }
                    StatusTag(p.country, Modifier.padding(top = 3.dp))
                }
                ChanceCell(chanceOf(p.country, "fighting"), Modifier.width(66.dp))
                ChanceCell(chanceOf(p.country, "major_war"), Modifier.width(54.dp))
            }
        }
        if (rows.size > SHOWN) {
            HorizontalDivider(color = Sage.line)
            TextButton(onClick = { all = !all }, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(if (all) "SHOW FEWER" else "SHOW ALL ${rows.size}", style = label(11.sp), color = Sage.text)
            }
        }
    }
}

private const val SHOWN = 15

@Composable
private fun SortHead(text: String, on: Boolean, desc: Boolean, modifier: Modifier, align: TextAlign, onClick: () -> Unit) {
    Text(text.uppercase() + if (on) (if (desc) " ↓" else " ↑") else "", style = label(10.sp), textAlign = align,
        color = if (on) Sage.text else Sage.muted,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 9.dp))
}

@Composable
private fun ChanceCell(p: Double?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Pct(p)
        if (p != null) MBar(p, Modifier.width(50.dp).padding(top = 5.dp))
    }
}
