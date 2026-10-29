package com.hostu404.sage.data

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import androidx.core.graphics.PathParser
import org.json.JSONObject
import kotlin.math.hypot

/**
 * The world map: Natural Earth's countries (public domain) on the Equal Earth
 * projection, the same file the website draws (assets/world.json, made by
 * tools/world_map.py in the SAGE project). Each country is keyed by its
 * three-letter code, as in the forecast file's iso3.
 */
class WorldShapes(val viewBox: RectF, val countries: Map<String, Shape>) {

    /** One country: its outline, the box around its main part, and a dot if it's too small to see. */
    class Shape(val code: String, val path: Path, val box: RectF, val dot: PointF?) {
        internal val bounds = RectF().also { path.computeBounds(it, true) }
    }

    private val regions = HashMap<String, Region>()

    /**
     * The country at a point on the map (in the map's own units), among
     * `codes`: an outline the point falls in, or else the nearest dot
     * within `reach`.
     */
    fun hit(x: Float, y: Float, codes: Set<String>, reach: Float): String? {
        for (code in codes) {
            val s = countries[code] ?: continue
            if (s.bounds.contains(x, y) && region(s).contains((x * SCALE).toInt(), (y * SCALE).toInt())) return code
        }
        return codes.mapNotNull { code -> countries[code]?.dot?.let { code to hypot(it.x - x, it.y - y) } }
            .filter { it.second <= reach }
            .minByOrNull { it.second }?.first
    }

    private fun region(s: Shape): Region = regions.getOrPut(s.code) {
        val scaled = Path(s.path).apply { transform(Matrix().apply { setScale(SCALE, SCALE) }) }
        val b = RectF().also { scaled.computeBounds(it, true) }
        Region().apply {
            setPath(scaled, Region(b.left.toInt() - 1, b.top.toInt() - 1, b.right.toInt() + 1, b.bottom.toInt() + 1))
        }
    }

    /**
     * The part of the map framing these countries, keeping the map's shape;
     * the whole world if they're spread too wide. As on the website.
     */
    fun frame(codes: List<String>): RectF {
        val boxes = codes.mapNotNull { countries[it]?.box }
        if (boxes.isEmpty()) return RectF(viewBox)
        val x0 = boxes.minOf { it.left }
        val y0 = boxes.minOf { it.top }
        val x1 = boxes.maxOf { it.right }
        val y1 = boxes.maxOf { it.bottom }
        var w = maxOf(x1 - x0, 50f) * 1.6f
        var h = maxOf(y1 - y0, 25f) * 1.6f
        val vw = viewBox.width()
        val vh = viewBox.height()
        if (w / h > vw / vh) h = w * vh / vw else w = h * vw / vh
        if (w > vw * 0.75f) return RectF(viewBox)
        val x = ((x0 + x1) / 2 - w / 2).coerceIn(viewBox.left, viewBox.right - w)
        val y = ((y0 + y1) / 2 - h / 2).coerceIn(viewBox.top, viewBox.bottom - h)
        return RectF(x, y, x + w, y + h)
    }

    companion object {
        /** Regions work in whole numbers: the map's units times this. */
        private const val SCALE = 10f

        @Volatile private var loaded: WorldShapes? = null

        /** The map if it's been read already, without reading it. */
        fun cached(): WorldShapes? = loaded

        /** The map, read once. */
        fun get(context: Context): WorldShapes = loaded ?: synchronized(this) {
            loaded ?: parse(context.assets.open("world.json").bufferedReader().use { it.readText() }).also { loaded = it }
        }

        fun parse(text: String): WorldShapes {
            val o = JSONObject(text)
            val v = o.getJSONArray("viewBox")
            val left = v.getDouble(0).toFloat()
            val top = v.getDouble(1).toFloat()
            val viewBox = RectF(left, top, left + v.getDouble(2).toFloat(), top + v.getDouble(3).toFloat())
            val all = o.getJSONObject("countries")
            val shapes = LinkedHashMap<String, Shape>()
            for (code in all.keys()) {
                val c = all.getJSONObject(code)
                val path = PathParser.createPathFromPathData(c.getString("d")) ?: continue
                val b = c.getJSONArray("box")
                val box = RectF(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat())
                val dot = c.optJSONArray("dot")?.let { PointF(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) }
                shapes[code] = Shape(code, path, box, dot)
            }
            return WorldShapes(viewBox, shapes)
        }
    }
}
