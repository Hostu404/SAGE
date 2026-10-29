package com.hostu404.sage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen
import com.hostu404.sage.data.Country
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Place
import com.hostu404.sage.data.Words

/** One country: as on the website's country page. */
@Composable
fun PlaceScreen(model: SageModel, f: Forecast, id: Int) {
    val place = f.place(id) ?: return
    val c = place.country
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Sage.bg)) {
        TopBar(title = place.name, onBack = { model.back() }, actions = {
            val on = id in model.followed
            IconButton(onClick = { model.toggleFollow(id) }) {
                Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, if (on) "Stop following" else "Follow",
                    tint = if (on) Sage.chance[1] else Sage.text)
            }
            IconButton(onClick = { Share.country(context, f, place) }) { Icon(Icons.Outlined.Share, "Share", tint = Sage.text) }
        })
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { CountryHead(place, f) }
            if (c != null) {
                item { Situation(c, f) }
                item { Chances(c, f) }
            } else item {
                Panel("Situation") {
                    Body("There's been no organised violence recorded in ${place.name} since 1989, so SAGE doesn't forecast " +
                        "fighting inside it. It does follow ${place.name}'s relations with other countries.", Modifier.padding(14.dp))
                }
            }
            item { RelationsMap(model, f, place) }
            item { Relations(model, f, id) }
            if (c != null && c.outlooks.values.any { it.why.isNotEmpty() }) item { Why(c, f) }
            item { Footer(made(f)) }
        }
    }
}

/** A panel without a title, like the website's head panels. */
@Composable
fun HeadBox(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Sage.panel).border(1.dp, Sage.line, shape), content = content)
}

@Composable
private fun CountryHead(place: Place, f: Forecast) {
    val c = place.country
    HeadBox {
        Column(Modifier.padding(16.dp)) {
            Text((c?.region ?: "Relations only").uppercase(), style = label(11.sp), color = Sage.muted)
            Text(place.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Sage.text,
                modifier = Modifier.padding(top = 4.dp))
            // A place with relations only says so above; nothing to add here.
            if (c != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusTag(c)
                Text("IN ${Words.month(f.dataThrough).uppercase()}", style = label(10.5.sp, FontWeight.Normal), color = Sage.faint)
                if (c.alert.on) Flag()
            }
        }
        if (c != null) {
            HorizontalDivider(color = Sage.line)
            StatGrid(com.hostu404.sage.data.Forecast.QUESTION_KEYS.mapNotNull { k ->
                chanceOf(c, k)?.let { p ->
                    StatItem(Words.pctShort(p), "${f.questions[k]?.title ?: k} in ${nextMonth(f)}: ${Words.chance(p)}", chanceColor(p))
                }
            })
        }
    }
}

@Composable
private fun Situation(c: Country, f: Forecast) {
    Panel("Situation") {
        Body(if (c.alert.on) c.summary.substringBefore(" Warning: ") else c.summary, Modifier.padding(14.dp))
        if (c.alert.on) {
            val shape = RoundedCornerShape(4.dp)
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth().clip(shape).background(Sage.alertBg)
                .border(1.dp, Sage.alert.copy(alpha = 0.45f), shape).padding(12.dp)) {
                Text("BUILD-UP ALERT", style = label(11.sp, FontWeight.Bold), color = Sage.alert)
                Body(alertText(c, f), Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The three questions by month, next month picked out, with the usual for countries like it. */
@Composable
private fun Chances(c: Country, f: Forecast) {
    val months = f.months
    val first = months.firstOrNull()?.let(Words::month) ?: ""
    val last = months.lastOrNull()?.let(Words::monthYear) ?: ""
    Panel("The chances", right = { Note("$first to $last") }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Spacer(Modifier.weight(1f))
            months.forEachIndexed { i, m ->
                Text(Words.monthShort(m).uppercase(), style = label(10.sp), textAlign = TextAlign.End,
                    color = if (i == com.hostu404.sage.data.Forecast.NEXT) Sage.text else Sage.muted, modifier = Modifier.width(CELL))
            }
            Text("USUAL", style = label(10.sp), color = Sage.muted, textAlign = TextAlign.End, modifier = Modifier.width(CELL))
        }
        HorizontalDivider(color = Sage.line)
        val keys = com.hostu404.sage.data.Forecast.QUESTION_KEYS.filter { c.outlooks[it] != null }
        keys.forEachIndexed { row, k ->
            val o = c.outlooks.getValue(k)
            if (row > 0) HorizontalDivider(color = Sage.line)
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.questions[k]?.title ?: k, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = Sage.text, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                months.indices.forEach { i ->
                    val p = o.chance.getOrNull(i)
                    Column(Modifier.width(CELL).background(if (i == com.hostu404.sage.data.Forecast.NEXT) Sage.panel2 else Sage.panel)
                        .padding(vertical = 12.dp), horizontalAlignment = Alignment.End) {
                        Pct(p)
                        if (p != null) MBar(p, Modifier.width(36.dp).padding(top = 6.dp))
                    }
                }
                Text(o.usual.getOrNull(com.hostu404.sage.data.Forecast.NEXT)?.let(Words::pctShort) ?: "–",
                    style = mono(13.5.sp, FontWeight.Normal), color = Sage.muted, textAlign = TextAlign.End, modifier = Modifier.width(CELL))
            }
        }
        HorizontalDivider(color = Sage.line)
        val defs = keys.mapNotNull { k -> f.questions[k]?.let { q -> "${q.title}: ${q.text.replaceFirstChar { it.lowercase() }}." } }
        Text((defs + "Usual: how often it happens in ${nextMonth(f)} to countries in the same situation.").joinToString(" "),
            style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.padding(14.dp))
    }
}

private val CELL = 52.dp

@Composable
private fun RelationsMap(model: SageModel, f: Forecast, place: Place) {
    val id = place.id
    val rel = f.relationsOf(id)
    val tension = rel.associate { it.otherId(id) to it.tension }
    var picked by remember { mutableStateOf<Place?>(null) }
    Panel("Relations map") {
        WorldMap(f, fill = { p ->
            when {
                p == null -> Sage.mapNone
                p.id == id -> Sage.text
                else -> tension[p.id]?.let(::tensionFill) ?: Sage.mapNone
            }
        }, description = "Map of ${place.name}'s relations with other countries",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            picked = setOfNotNull(id, picked?.id),
            zoom = listOf(id) + rel.filter { it.tension != "usual" }.map { it.otherId(id) },
            marks = setOf(id) + rel.filter { it.tension != "usual" }.map { it.otherId(id) },
            onTap = { picked = it })
        val pick = picked
        if (pick != null) {
            val r = rel.firstOrNull { it.otherId(id) == pick.id }
            MapPick(pick,
                when { pick.id == id -> "This country"; r != null -> "Tension: ${r.tension} · clashes in six months: ${Words.pctShort(r.clash6)}"; else -> "No relation SAGE follows" },
                if (pick.id == id) null else "Open ›") {
                if (r != null) model.open(Screen.Pair(r.a, r.b)) else model.open(Screen.Place(pick.id))
            }
        }
        Legend(listOf(place.name to Sage.text, "At war or fighting" to Sage.map[5], "Very high" to Sage.map[4],
            "High" to Sage.map[3], "Above usual" to Sage.map[2], "Usual" to Sage.map[0]))
    }
}

@Composable
private fun Relations(model: SageModel, f: Forecast, id: Int) {
    val rel = f.relationsOf(id)
    Panel("Relations", right = { Note("Next six months") }) {
        if (rel.isEmpty()) {
            Body("None that SAGE follows.", Modifier.padding(14.dp), Sage.muted)
            return@Panel
        }
        RowsHead("Country", "Tension", "Clashes")
        rel.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Sage.line)
            ListRow({ model.open(Screen.Pair(r.a, r.b)) }) {
                Name(r.other(id), Modifier.weight(1f))
                Tag(r.tension, tensionColor(r.tension))
                Pct(r.clash6, Modifier.width(44.dp))
            }
        }
    }
}

@Composable
private fun Why(c: Country, f: Forecast) {
    Panel("Why") {
        val keys = com.hostu404.sage.data.Forecast.QUESTION_KEYS.filter { c.outlooks[it]?.why?.isNotEmpty() == true }
        keys.forEachIndexed { i, k ->
            if (i > 0) HorizontalDivider(color = Sage.line)
            Column(Modifier.padding(14.dp)) {
                Text((f.questions[k]?.title ?: k).uppercase(), style = label(11.sp), color = Sage.muted)
                Bullets(c.outlooks.getValue(k).why, Modifier.padding(top = 8.dp))
            }
        }
    }
}
