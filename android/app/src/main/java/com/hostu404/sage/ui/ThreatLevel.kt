package com.hostu404.sage.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hostu404.sage.data.World

/** The five levels in the colours of the old US threat advisory: green, blue, yellow, orange, red. */
val THREAT_COLORS = listOf(
    Color(0xFF3B9C46), Color(0xFF2F6CC9), Color(0xFFF2C12E), Color(0xFFF07D22), Color(0xFFD22B2B),
)

/** Text that reads on each level's colour. */
private val ON_THREAT = listOf(Color.White, Color.White, Color(0xFF1B1C19), Color(0xFF1B1C19), Color.White)

/** The level, in capitals, on its colour. */
@Composable
fun ThreatLevel(w: World, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = THREAT_COLORS[w.rank],
        modifier = modifier.semantics { contentDescription = "Global threat level: ${w.level}" }) {
        Text(w.level.uppercase(), color = ON_THREAT[w.rank], fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
    }
}
