package com.hostu404.sage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hostu404.sage.R
import com.hostu404.sage.data.Country
import com.hostu404.sage.data.Words

/** A dashboard panel: a titled box, with an optional note or control on the right of the title. */
@Composable
fun Panel(title: String, modifier: Modifier = Modifier, right: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Sage.panel).border(1.dp, Sage.line, shape)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = label(11.5.sp), color = Sage.text, modifier = Modifier.weight(1f).semantics { heading() })
            right?.invoke()
        }
        HorizontalDivider(color = Sage.line)
        content()
    }
}

/** The small note on the right of a panel's title. */
@Composable
fun Note(text: String) = Text(text.uppercase(), style = label(10.5.sp), color = Sage.muted, maxLines = 1)

/** A word with a small square of colour: "■ AT WAR". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Text(text.uppercase(), style = label(10.5.sp), color = Sage.muted, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The red "ALERT" flag of a build-up alert. */
@Composable
fun Flag(modifier: Modifier = Modifier) = Text("ALERT", style = label(10.sp, FontWeight.Bold), color = Sage.bg,
    modifier = modifier.clip(RoundedCornerShape(3.dp)).background(Sage.alert).padding(horizontal = 5.dp, vertical = 2.dp))

fun chanceColor(p: Double): Color = Sage.chance[Words.level(p)]

/** A thin bar for a chance, in its colour. */
@Composable
fun MBar(p: Double, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Sage.line).clearAndSetSemantics { }) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.02, 1.0).toFloat()).fillMaxHeight().background(chanceColor(p)))
    }
}

/** A chance as a number, in its colour: "94%", "<1%". */
@Composable
fun Pct(p: Double?, modifier: Modifier = Modifier, short: Boolean = true) = Text(
    when { p == null -> "–"; short -> Words.pctShort(p); else -> Words.pct(p) },
    style = mono(13.5.sp), color = if (p == null) Sage.faint else chanceColor(p), textAlign = TextAlign.End, maxLines = 1,
    modifier = modifier,
)

data class StatItem(val value: String, val label: String, val color: Color = Sage.text)

/** Big numbers with a line under each, two to a row, with lines between them. */
@Composable
fun StatGrid(stats: List<StatItem>, modifier: Modifier = Modifier) {
    Column(modifier) {
        stats.chunked(2).forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = Sage.line)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { j, s ->
                    if (j > 0) VerticalDivider(color = Sage.line)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(s.value, style = mono(26.sp, FontWeight.Bold), color = s.color)
                        Text(s.label, style = MaterialTheme.typography.bodySmall, color = Sage.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

fun statusColor(c: Country?): Color = when (c?.status) {
    null -> Sage.calm
    "At war" -> Sage.chance[3]
    "Fighting" -> Sage.chance[2]
    "Some clashes", "Attacks on civilians" -> Sage.chance[1]
    else -> Sage.chance[0]
}

/** What each status means, for screen readers and the country page. */
fun statusMeans(status: String): String = when (status) {
    "At war" -> "1,000 or more killed in fighting involving the government last month"
    "Fighting" -> "25 or more killed in fighting involving the government last month"
    "Some clashes" -> "Some killed in fighting involving the government last month"
    "Attacks on civilians" -> "Armed groups killed 10 or more civilians last month"
    "Calm" -> "No deaths in fighting involving the government last month"
    else -> "No organised violence here since 1989. SAGE follows its relations with other countries"
}

@Composable
fun StatusTag(c: Country?, modifier: Modifier = Modifier) = Tag(c?.status ?: "Relations only", statusColor(c), modifier)

fun tensionColor(t: String): Color = when (t) {
    "at war", "fighting" -> Sage.chance[3]
    "very high" -> Sage.chance[2]
    "high" -> Sage.chance[1]
    "above usual" -> Sage.chance[1].copy(alpha = 0.6f)
    else -> Sage.calm
}

/** Tension on a map, in the map's shades. */
fun tensionFill(t: String): Color = when (t) {
    "at war", "fighting" -> Sage.map[5]
    "very high" -> Sage.map[4]
    "high" -> Sage.map[3]
    "above usual" -> Sage.map[2]
    else -> Sage.map[0]
}

/** A chance on the map: no forecast, then the six shades. */
fun mapColor(p: Double?): Color = if (p == null) Sage.mapNone else Sage.map[listOf(0.01, 0.05, 0.2, 0.4, 0.8).count { p >= it }]

/** Two or three choices side by side, the chosen one filled in. */
@Composable
fun Toggle(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Row(Modifier.clip(shape).border(1.dp, Sage.lineStrong, shape).height(IntrinsicSize.Min)) {
        options.forEachIndexed { i, (key, text) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Sage.lineStrong))
            val on = key == selected
            Text(text.uppercase(), style = label(10.5.sp), color = if (on) Sage.bg else Sage.muted,
                modifier = Modifier.background(if (on) Sage.text else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(key) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 10.dp, vertical = 7.dp))
        }
    }
}

/** One line of a list: name, then whatever else, the whole line tappable. */
@Composable
fun ListRow(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Column names over a list, as on the website: "COUNTRIES   TENSION   CLASHES". */
@Composable
fun RowsHead(first: String, vararg rest: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(first.uppercase(), style = label(10.sp), color = Sage.faint, modifier = Modifier.weight(1f))
        rest.forEach { Text(it.uppercase(), style = label(10.sp), color = Sage.faint) }
    }
    HorizontalDivider(color = Sage.line)
}

@Composable
fun Name(text: String, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal) =
    Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = weight, color = Sage.text, maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = modifier)

/** Summary lines, each with a "›", as on the website. */
@Composable
fun Lines(lines: List<String>) {
    lines.forEachIndexed { i, line ->
        if (i > 0) HorizontalDivider(color = Sage.line)
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("›", style = mono(14.sp), color = Sage.faint, modifier = Modifier.width(18.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Sage.text)
        }
    }
}

@Composable
fun Bullets(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        lines.forEach { line ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = Sage.faint)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Sage.text, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Sage.text) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)

/** The bar across the top: SAGE's mark on the home screen, a back arrow everywhere else. */
@Composable
fun TopBar(title: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.background(Sage.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Sage.text) }
            } else {
                Spacer(Modifier.width(12.dp))
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(28.dp))
                Text("SAGE", style = mono(16.sp, FontWeight.Bold).copy(letterSpacing = 0.24.em), color = Sage.text,
                    modifier = Modifier.padding(start = 10.dp))
            }
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Sage.text, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 2.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            actions()
        }
        HorizontalDivider(color = Sage.line)
    }
}

/** The lines every screen ends with. */
@Composable
fun Footer(made: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 28.dp)) {
        HorizontalDivider(color = Sage.line)
        Text("SAGE is a statistical forecast, not a warning service. For travel and safety, follow official advice.",
            style = MaterialTheme.typography.bodySmall, color = Sage.faint, modifier = Modifier.padding(top = 14.dp))
        if (made != null) Text(made, style = MaterialTheme.typography.bodySmall, color = Sage.faint, modifier = Modifier.padding(top = 6.dp))
    }
}
