package com.hostu404.sage.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Place
import com.hostu404.sage.data.WorldShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * The world map, as on the website. `fill` colours each country (null for
 * those SAGE has nothing on), `picked` are outlined, `zoom` frames those
 * countries, and a tap on a country calls `onTap`. Countries in `marks` too
 * small to see or tap at this zoom get a ring around them; on a phone there's
 * no pointer to find them with.
 */
@Composable
fun WorldMap(
    f: Forecast,
    fill: (Place?) -> Color,
    description: String,
    modifier: Modifier = Modifier,
    picked: Set<Int> = emptySet(),
    zoom: List<Int>? = null,
    marks: Set<Int> = emptySet(),
    onTap: (Place) -> Unit = {},
) {
    val context = LocalContext.current
    var shapes by remember { mutableStateOf(WorldShapes.cached()) }
    if (shapes == null) LaunchedEffect(Unit) { shapes = withContext(Dispatchers.Default) { WorldShapes.get(context) } }
    val world = shapes
    if (world == null) {
        Box(modifier.fillMaxWidth().aspectRatio(2.2f), contentAlignment = Alignment.Center) {
            Text("Loading the map…", style = MaterialTheme.typography.bodySmall, color = Sage.muted)
        }
        return
    }
    val byCode = remember(f) { f.iso3.mapNotNull { (id, code) -> f.place(id)?.let { code to it } }.toMap() }
    val paths = remember(world) { world.countries.mapValues { it.value.path.asComposePath() } }
    val view = remember(world, zoom) {
        zoom?.let { ids -> world.frame(ids.mapNotNull { f.iso3[it] }) } ?: RectF(world.viewBox)
    }
    val marked = remember(world, marks, f) { marks.mapNotNull { id -> f.iso3[id]?.let(world.countries::get) } }
    fun small(scale: Float, px: Float) = marked.filter { maxOf(it.box.width(), it.box.height()) * scale < px }
    fun centre(s: WorldShapes.Shape) = s.dot?.let { Offset(it.x, it.y) } ?: Offset(s.box.centerX(), s.box.centerY())
    Canvas(
        modifier.fillMaxWidth().aspectRatio(view.width() / view.height()).clipToBounds()
            .semantics { contentDescription = description }
            .pointerInput(world, view, byCode, marked) {
                detectTapGestures { pos ->
                    val scale = size.width / view.width()
                    val x = view.left + pos.x / scale
                    val y = view.top + pos.y / scale
                    // A ring first: it's drawn over its neighbours.
                    val ringed = small(scale, SMALL.toPx())
                        .map { it.code to hypot(centre(it).x - x, centre(it).y - y) }
                        .filter { it.second <= (RING + 6.dp).toPx() / scale }
                        .minByOrNull { it.second }?.first
                    val code = ringed ?: world.hit(x, y, byCode.keys, reach = 12f / scale)
                    code?.let { byCode[it] }?.let(onTap)
                }
            },
    ) {
        val scale = size.width / view.width()
        val dotRadius = view.width() / 380f
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(-view.left, -view.top)
        }) {
            for ((code, path) in paths) {
                drawPath(path, fill(byCode[code]))
                drawPath(path, Sage.bg, style = Stroke(width = 0.6f / scale))
            }
            // Countries too small to see get a dot.
            for ((code, shape) in world.countries) {
                val dot = shape.dot ?: continue
                val place = byCode[code] ?: continue
                drawCircle(fill(place), dotRadius, Offset(dot.x, dot.y))
                drawCircle(Sage.bg, dotRadius, Offset(dot.x, dot.y), style = Stroke(width = 0.6f / scale))
            }
            for ((code, path) in paths) {
                if (byCode[code]?.id in picked) drawPath(path, Sage.text, style = Stroke(width = 1.5f / scale))
            }
            for (s in small(scale, SMALL.toPx())) {
                drawCircle(fill(byCode[s.code]), RING.toPx() / scale, centre(s), style = Stroke(width = 1.5.dp.toPx() / scale))
            }
        }
    }
}

/** A marked country narrower and shorter than this on screen gets a ring this size. */
private val SMALL = 12.dp
private val RING = 7.dp

/** What a tap on the map picked: its name, a line about it, and a way in. */
@Composable
fun MapPick(place: Place?, line: String, openLabel: String?, onOpen: () -> Unit) {
    if (place == null) return
    HorizontalDivider(color = Sage.line)
    ListRow(onClick = onOpen.takeIf { openLabel != null }, modifier = Modifier.background(Sage.panel2)) {
        Column(Modifier.weight(1f)) {
            Name(place.name)
            Text(line, style = MaterialTheme.typography.bodySmall, color = Sage.muted)
        }
        if (openLabel != null) TextButton(onClick = onOpen) { Text(openLabel.uppercase(), style = label(11.sp), color = Sage.text) }
    }
}

/** The key under a map: a small square of each colour and what it means. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (text, color) -> Tag(text, color) }
    }
}

/** The key for chances: no forecast, then the six shades. */
val RISK_LEGEND: List<Pair<String, Color>>
    get() = listOf("Not forecast" to Sage.mapNone) + Sage.mapLabels.zip(Sage.map)
