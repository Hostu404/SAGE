package com.hostu404.sage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hostu404.sage.data.Country
import com.hostu404.sage.data.Words

/** How likely, as a word and a number, with a bar. Colour only ever repeats what the words say. */
@Composable
fun ChanceLine(p: Double, modifier: Modifier = Modifier, label: String? = null) {
    val color = LocalRisk.current.levels[Words.level(p)]
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Text(Words.chance(p).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold,
                modifier = if (label == null) Modifier.weight(1f) else Modifier)
            Text("  ${Words.pct(p)}", style = MaterialTheme.typography.titleMedium, color = color)
        }
        ChanceBar(p, color, Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ChanceBar(p: Double, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics { }
    ) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.015, 1.0).toFloat()).height(8.dp).background(color))
    }
}

/** A small label such as "Fighting" or "Tension: high". */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
fun statusColor(c: Country?): Color {
    val risk = LocalRisk.current
    return when (c?.status) {
        null -> risk.calm
        "At war" -> risk.levels[3]
        "Fighting" -> risk.levels[2]
        "Some clashes", "Attacks on civilians" -> risk.levels[1]
        else -> risk.levels[0]
    }
}

@Composable
fun tensionColor(t: String): Color {
    val risk = LocalRisk.current
    return when (t) {
        "at war", "fighting" -> risk.levels[3]
        "very high" -> risk.levels[2]
        "high" -> risk.levels[1]
        "above usual" -> risk.levels[1].copy(alpha = 0.7f)
        else -> risk.calm
    }
}

@Composable
fun AlertChip() = Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.errorContainer) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
        Text("Alert", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
)

@Composable
fun SageCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp)) { content() }
    } else Card(modifier = modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun Bullets(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** The line every screen ends with. */
@Composable
fun Disclaimer(modifier: Modifier = Modifier) = Text(
    "SAGE is a statistical forecast, not a warning service. For travel and safety, follow official advice.",
    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
)
