package com.hostu404.sage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hostu404.sage.data.World

/** The five levels in the colours of the old US threat advisory: green, blue, yellow, orange, red. */
val THREAT_COLORS = listOf(
    Color(0xFF3B9C46), Color(0xFF2F6CC9), Color(0xFFF2C12E), Color(0xFFF07D22), Color(0xFFD22B2B),
)

/** Text that reads on each level's colour. */
val ON_THREAT = listOf(Color.White, Color.White, Color(0xFF111111), Color(0xFF111111), Color.White)

/** The level in capitals on its colour, with the five-step meter under it, as on the website. */
@Composable
fun ThreatBlock(w: World, modifier: Modifier = Modifier) {
    val levels = World.LEVELS
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "Global threat level: ${w.level}" }) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(THREAT_COLORS[w.rank]).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center) {
            Text(w.level.uppercase(), style = mono(26.sp, FontWeight.ExtraBold).copy(letterSpacing = 0.16.em), color = ON_THREAT[w.rank])
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            levels.indices.forEach { i ->
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(1.dp))
                    .background(THREAT_COLORS[i].copy(alpha = if (i == w.rank) 1f else 0.22f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(levels.first().uppercase(), style = label(10.sp, FontWeight.Normal), color = Sage.faint)
            Spacer(Modifier.weight(1f))
            Text(levels.last().uppercase(), style = label(10.sp, FontWeight.Normal), color = Sage.faint)
        }
    }
}
