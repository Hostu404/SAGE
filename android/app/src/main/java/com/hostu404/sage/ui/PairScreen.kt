package com.hostu404.sage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Words

/** Two countries: as on the website's pair page. */
@Composable
fun PairScreen(model: SageModel, f: Forecast, a: Int, b: Int) {
    val r = f.relation(a, b) ?: return
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Sage.bg)) {
        TopBar(title = r.title, onBack = { model.back() }, actions = {
            IconButton(onClick = { Share.pair(context, f, r) }) { Icon(Icons.Outlined.Share, "Share", tint = Sage.text) }
        })
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HeadBox {
                    Column(Modifier.padding(16.dp)) {
                        Text("BETWEEN COUNTRIES", style = label(11.sp), color = Sage.muted)
                        Text(r.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Sage.text,
                            modifier = Modifier.padding(top = 4.dp))
                        Tag("Tension: ${r.tension}", tensionColor(r.tension), Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(r.a to r.names[0], r.b to r.names[1]).forEach { (id, name) ->
                                OutlinedButton(onClick = { model.open(Screen.Place(id)) }) { Text(name.uppercase(), style = label(11.sp), color = Sage.text) }
                            }
                        }
                    }
                    HorizontalDivider(color = Sage.line)
                    StatGrid(listOfNotNull(
                        StatItem(Words.pctShort(r.clash6), "Deadly clashes in the next six months: ${Words.chance(r.clash6)}", chanceColor(r.clash6)),
                        StatItem(Words.pctShort(r.war6), "War in the next six months: ${Words.chance(r.war6)}", chanceColor(r.war6)),
                        r.usual6?.let { StatItem(Words.pctShort(it), "Usual chance of clashes for two countries like these") },
                    ))
                }
            }
            item {
                Panel("Situation") {
                    Body(r.summary, Modifier.padding(14.dp))
                    Text("Deadly clashes: at least one death in fighting between the two governments' forces. War: 1,000 or more deaths in a month.",
                        style = MaterialTheme.typography.bodySmall, color = Sage.muted,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
                }
            }
            item {
                Panel("Map") {
                    WorldMap(f, fill = { p ->
                        when (p?.id) {
                            r.a -> Sage.text
                            r.b -> Sage.pick2
                            else -> Sage.mapNone
                        }
                    }, description = "Map of ${r.title}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        picked = setOf(r.a, r.b), zoom = listOf(r.a, r.b), marks = setOf(r.a, r.b),
                        onTap = { p -> if (p.id == r.a || p.id == r.b) model.open(Screen.Place(p.id)) })
                    Legend(listOf(r.names[0] to Sage.text, r.names[1] to Sage.pick2))
                }
            }
            item { Footer(made(f)) }
        }
    }
}
