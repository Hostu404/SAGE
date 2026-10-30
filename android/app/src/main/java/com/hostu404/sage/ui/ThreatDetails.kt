package com.hostu404.sage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Words
import com.hostu404.sage.data.World
import java.time.YearMonth
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The parts of the global threat level that open what they're made of, as on the website. */
object ThreatPart {
    const val LEVEL = "level"
    const val FIGHTING = "fighting"
    const val WAR = "major_war"
    const val HISTORY = "history"
    const val ALERTS = "alerts"
}

/** One part of the threat level, opened from the bottom of the screen. Tapping a country opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatSheet(part: String, w: World, f: Forecast, onOpen: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Sage.panel,
        contentColor = Sage.text,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        ThreatDetail(part, w, f, onOpen)
    }
}

/** What one part of the threat level is made of. */
@Composable
fun ThreatDetail(part: String, w: World, f: Forecast, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 28.dp)) {
        when (part) {
            ThreatPart.LEVEL -> levelItems(w, f)
            ThreatPart.HISTORY -> historyItems(w, f)
            ThreatPart.ALERTS -> alertItems(f, onOpen)
            else -> expectedItems(part, w, f, onOpen)
        }
    }
}

private fun LazyListScope.expectedItems(key: String, w: World, f: Forecast, onOpen: (Int) -> Unit) {
    val war = key == ThreatPart.WAR
    val month = w.month?.let(Words::month) ?: nextMonth(f)
    val last = w.lastMonth?.let(Words::month) ?: "last month"
    val expected = if (war) w.expectedWars else w.expectedFighting
    val threshold = if (war) 1000 else 25
    val what = if (war) "full-scale war" else "deadly fighting"
    val rule = if (war) "1,000 or more killed in fighting involving the government"
    else "25 or more killed in fighting involving the government"
    val ranked = f.countries.filter { chanceOf(it, key) != null }.sortedByDescending { chanceOf(it, key) }
    val shown = maxOf(5, expected.roundToInt())
    val rest = ranked.drop(shown)
    val had = f.countries.filter { it.lastMonth.state >= threshold }.sortedByDescending { it.lastMonth.state }

    item { SheetTitle("${what.replaceFirstChar { it.uppercase() }} in $month") }
    item {
        SheetLead("SAGE gives every country a chance of $what in $month ($rule). Added up, they come to about " +
            "${tenths(expected)} countries. The $shown most likely:")
    }
    item { SheetHeading("Most likely in $month") }
    itemsIndexed(ranked.take(shown)) { i, c ->
        val p = chanceOf(c, key) ?: 0.0
        if (i > 0) HorizontalDivider(color = Sage.line)
        ListRow({ onOpen(c.id) }) {
            Text("${i + 1}", style = mono(12.sp, FontWeight.Normal), color = Sage.faint, modifier = Modifier.width(20.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Name(c.name, Modifier.weight(1f, fill = false))
                // Likely now, and not last month.
                if (c.lastMonth.state < threshold && p >= 0.4) Text("NEW", style = label(10.sp, FontWeight.Bold), color = Sage.chance[1])
                if (c.alert.on) Flag()
            }
            MBar(p, Modifier.width(56.dp))
            Pct(p, Modifier.width(44.dp))
        }
    }
    if (rest.isNotEmpty()) item { SheetNote("The other ${rest.size} countries add up to about ${tenths(rest.sumOf { chanceOf(it, key) ?: 0.0 })}.") }
    item { SheetHeading("Had it in $last: ${had.size}") }
    if (had.isEmpty()) item { SheetNote("None.") }
    else item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("COUNTRY", style = label(10.sp), color = Sage.faint, modifier = Modifier.weight(1f))
            Text(month.take(3).uppercase(), style = label(10.sp), color = Sage.faint, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            Text("KILLED", style = label(10.sp), color = Sage.faint, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
        }
        HorizontalDivider(color = Sage.line)
    }
    itemsIndexed(had) { i, c ->
        if (i > 0) HorizontalDivider(color = Sage.line)
        ListRow({ onOpen(c.id) }) {
            Name(c.name, Modifier.weight(1f))
            Pct(chanceOf(c, key), Modifier.width(44.dp))
            Text("%,d".format(Locale.UK, c.lastMonth.state), style = mono(13.5.sp), color = Sage.text, textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp))
        }
    }
}

private fun LazyListScope.historyItems(w: World, f: Forecast) {
    val since = w.since ?: "1989-01"
    val month = w.month?.let(Words::month) ?: nextMonth(f)
    val lead = if (w.calmerFighting != null && w.calmerWars != null) {
        "In ${pct(w.calmerFighting)} of months since ${since.take(4)}, fewer countries had deadly fighting than the " +
            "${tenths(w.expectedFighting)} SAGE expects in $month. In ${pct(w.calmerWars)} of them, fewer were at full-scale " +
            "war than the ${tenths(w.expectedWars)} it expects. The level takes the average: ${pct(w.score)}."
    } else {
        "Since ${since.take(4)}, ${pct(w.score)} of months were calmer than SAGE expects $month to be, going by how many " +
            "countries had deadly fighting and full-scale war."
    }
    item { SheetTitle("${pct(w.score)} of months were calmer") }
    item { SheetLead(lead) }
    if (w.historyFighting.isNotEmpty()) {
        item { HistoryChart(w.historyFighting, w.expectedFighting, since, "Countries with deadly fighting, each month") }
        item { HistoryChart(w.historyWars, w.expectedWars, since, "Countries at full-scale war, each month") }
    }
    item { SheetNote(levelLine(w)) }
}

private fun LazyListScope.levelItems(w: World, f: Forecast) {
    val month = w.month?.let(Words::month) ?: nextMonth(f)
    item { SheetTitle("Global threat level: ${w.level}") }
    item {
        SheetLead("The level compares how many countries SAGE expects to see deadly fighting and full-scale war in $month " +
            "with every month since 1989: the more months that were calmer, the higher it is.")
    }
    itemsIndexed(World.LEVELS) { i, name ->
        if (i > 0) HorizontalDivider(color = Sage.line)
        Row(Modifier.fillMaxWidth().background(if (i == w.rank) Sage.panel2 else Sage.panel).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(THREAT_COLORS[i]))
            Text(name.uppercase(), style = label(11.sp, FontWeight.Bold), color = Sage.text, modifier = Modifier.width(78.dp))
            Text(range(i), style = MaterialTheme.typography.bodySmall, color = if (i == w.rank) Sage.text else Sage.muted,
                modifier = Modifier.weight(1f))
            if (i == w.rank) Text("now: ${pct(w.score)}", style = mono(11.sp), color = Sage.text)
        }
    }
}

private fun LazyListScope.alertItems(f: Forecast, onOpen: (Int) -> Unit) {
    val alerts = f.countries.filter { it.alert.on }
    item { SheetTitle(if (alerts.size == 1) "1 build-up alert" else "${alerts.size} build-up alerts") }
    item {
        SheetLead("An alert goes out when news of troop build-ups, military alerts and threats of force against a country is " +
            "far above its usual. It's a warning sign, not a forecast: most build-ups never turn into war.")
    }
    if (alerts.isEmpty()) item { SheetNote("No build-up alerts right now.") }
    itemsIndexed(alerts) { i, c ->
        if (i > 0) HorizontalDivider(color = Sage.line)
        Column(Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Name(c.name, weight = FontWeight.SemiBold)
                StatusTag(c)
            }
            val p = chanceOf(c, "fighting")
            Text(alertShort(c) + (p?.let { " Deadly fighting in ${nextMonth(f)}: ${Words.pctShort(it)}." } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Every month on record as a line, with what SAGE expects next month as a dashed line across it. */
@Composable
private fun HistoryChart(values: List<Int>, expected: Double, since: String, title: String) {
    val first = runCatching { YearMonth.parse(since) }.getOrDefault(YearMonth.of(1989, 1))
    val top = maxOf(1, ceil(maxOf(values.max().toDouble(), expected) * 1.1).toInt())
    val most = values.max()
    val mostMonth = Words.monthYear(first.plusMonths(values.indexOf(most).toLong()).toString())
    val measurer = rememberTextMeasurer()
    val tick = mono(9.sp, FontWeight.Normal).copy(color = Sage.faint)
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(title.uppercase(), style = label(10.5.sp), color = Sage.muted)
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp).border(1.dp, Sage.line, RoundedCornerShape(4.dp))
            .semantics { contentDescription = "$title since ${first.year}. SAGE expects ${tenths(expected)} next month." }) {
            val left = 26.dp.toPx()
            val right = 8.dp.toPx()
            val above = 8.dp.toPx()
            val below = 18.dp.toPx()
            val width = size.width - left - right
            val height = size.height - above - below
            fun x(i: Int) = left + i / (values.size - 1).coerceAtLeast(1).toFloat() * width
            fun y(v: Double) = above + height - (v / top * height).toFloat()
            for (v in listOf(0, (top / 2.0).roundToInt(), top)) {
                drawLine(Sage.line, Offset(left, y(v.toDouble())), Offset(left + width, y(v.toDouble())), 1.dp.toPx())
                val t = measurer.measure("$v", tick)
                drawText(t, topLeft = Offset(left - 4.dp.toPx() - t.size.width, y(v.toDouble()) - t.size.height / 2f))
            }
            var year = (first.year + 9) / 10 * 10
            while (true) {
                val i = (year - first.year) * 12 - (first.monthValue - 1)
                if (i >= values.size) break
                val t = measurer.measure("$year", tick)
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2f, size.height - t.size.height - 2.dp.toPx()))
                year += 10
            }
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v.toDouble())) else path.lineTo(x(i), y(v.toDouble())) }
            drawPath(path, Sage.chance[2], style = Stroke(width = 1.5.dp.toPx()))
            drawLine(Sage.text, Offset(left, y(expected)), Offset(left + width, y(expected)), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        Text("- - Expected next month: ${tenths(expected)}. Most on record: $most, in $mostMonth.",
            style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SheetTitle(text: String) =
    Text(text.uppercase(), style = label(12.sp), color = Sage.text, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp))

@Composable
private fun SheetLead(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Sage.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))

@Composable
private fun SheetHeading(text: String) {
    Text(text.uppercase(), style = label(10.5.sp), color = Sage.muted, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp))
    HorizontalDivider(color = Sage.line)
}

@Composable
private fun SheetNote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))

private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

/** 18.8 as "18.8", 2.0 as "2". */
private fun tenths(x: Double): String {
    val r = (x * 10).roundToInt()
    return if (r % 10 == 0) "${r / 10}" else "%.1f".format(Locale.UK, r / 10.0)
}

private fun range(i: Int): String {
    val from = (World.FROM[i] * 100).roundToInt()
    return if (i == World.FROM.lastIndex) "$from% or more of months calmer"
    else "$from% to ${(World.FROM[i + 1] * 100).roundToInt()}% of months calmer"
}

/** "High means 80% to 95% of months were calmer. 95% or more would be Severe." */
private fun levelLine(w: World): String {
    val r = w.rank.coerceIn(0, World.LEVELS.lastIndex)
    val from = (World.FROM[r] * 100).roundToInt()
    if (r == World.LEVELS.lastIndex) return "${w.level} means $from% or more of months were calmer."
    val to = (World.FROM[r + 1] * 100).roundToInt()
    return "${w.level} means $from% to $to% of months were calmer. $to% or more would be ${World.LEVELS[r + 1]}."
}
