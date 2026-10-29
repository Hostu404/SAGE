package com.hostu404.sage.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hostu404.sage.R
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Place
import com.hostu404.sage.data.Relation
import com.hostu404.sage.data.Words
import java.io.File

/** Sending a forecast to someone: a picture card and a line of text with a link to its page on the website. */
object Share {
    const val SITE = "https://wysesage.xyz"

    fun countryText(f: Forecast, place: Place): String {
        val c = place.country
        val parts = listOfNotNull(
            chanceOf(c, "fighting")?.let { "deadly fighting in ${nextMonth(f)} ${Words.chance(it)} (${Words.pct(it)})" },
            chanceOf(c, "major_war")?.let { "full-scale war ${Words.chance(it)} (${Words.pct(it)})" },
        )
        val body = if (parts.isEmpty()) "SAGE follows ${place.name}'s relations with other countries."
        else "SAGE forecast for ${place.name}: ${parts.joinToString(", ")}." + if (c?.alert?.on == true) " Build-up alert on." else ""
        return "$body\n$SITE/#/country/${place.id}"
    }

    fun pairText(r: Relation): String =
        "SAGE: tension between ${r.names[0]} and ${r.names[1]} is ${r.tension}. Deadly clashes in the next six months: " +
            "${Words.chance(r.clash6)} (${Words.pct(r.clash6)}); war: ${Words.chance(r.war6)} (${Words.pct(r.war6)}).\n" +
            "$SITE/#/pair/${r.a}-${r.b}"

    fun country(context: Context, f: Forecast, place: Place) =
        send(context, countryText(f, place), ShareCard.country(context, f, place), "sage-${place.id}.png")

    fun pair(context: Context, f: Forecast, r: Relation) =
        send(context, pairText(r), ShareCard.pair(context, f, r), "sage-${r.a}-${r.b}.png")

    private fun send(context: Context, text: String, card: Bitmap, name: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}

/** The picture: SAGE's colours, the place, and its chances with bars, square for chat apps. */
object ShareCard {
    private const val SIZE = 1080
    private const val M = 72f

    /** One row of the card: what, how likely in words (unless `word` is off), and the number. */
    private data class Line(val label: String, val p: Double, val word: Boolean = true)

    fun country(context: Context, f: Forecast, place: Place): Bitmap {
        val c = place.country
        val lines = Forecast.QUESTION_KEYS.mapNotNull { k ->
            chanceOf(c, k)?.let { Line("${f.questions[k]?.title ?: k} in ${nextMonth(f)}", it) }
        }
        val status = c?.let { "${it.status} last month" } ?: "Relations only"
        return draw(context, f, (c?.region ?: "Relations only"), place.name, status, statusColor(c), c?.alert?.on == true, lines)
    }

    fun pair(context: Context, f: Forecast, r: Relation): Bitmap = draw(
        context, f, "Between countries", r.title, "Tension: ${r.tension}", tensionColor(r.tension), false,
        listOfNotNull(Line("Deadly clashes in the next six months", r.clash6), Line("War in the next six months", r.war6),
            r.usual6?.let { Line("Usual for two countries like these", it, word = false) }),
    )

    private fun draw(
        context: Context, f: Forecast, kicker: String, name: String, status: String, statusColor: Color,
        alert: Boolean, lines: List<Line>,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Sage.bg.toArgb())
        val mono = Typeface.MONOSPACE
        val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        fun paint(color: Color, size: Float, face: Typeface, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb(); textSize = size; typeface = face; letterSpacing = spacing
        }
        val right = SIZE - M

        // SAGE's mark, and the global threat level on its colour.
        ContextCompat.getDrawable(context, R.drawable.ic_logo)?.apply { setBounds(M.toInt(), 58, M.toInt() + 84, 142); draw(canvas) }
        canvas.drawText("SAGE", M + 104, 122f, paint(Sage.text, 54f, monoBold, 0.24f))
        f.world?.let { w ->
            val p = paint(ON_THREAT[w.rank], 30f, monoBold, 0.12f)
            val text = "GLOBAL: ${w.level.uppercase()}"
            val width = p.measureText(text) + 44
            val box = RectF(right - width, 70f, right, 130f)
            canvas.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = THREAT_COLORS[w.rank].toArgb() })
            canvas.drawText(text, box.left + 22, 111f, p)
        }
        canvas.drawRect(M, 178f, right, 180f, Paint().apply { color = Sage.line.toArgb() })

        canvas.drawText(kicker.uppercase(), M, 262f, paint(Sage.muted, 32f, mono, 0.08f))
        val title = paint(Sage.text, 92f, Typeface.DEFAULT_BOLD)
        while (title.measureText(name) > right - M && title.textSize > 48f) title.textSize -= 2f
        canvas.drawText(name, M, 360f, title)

        canvas.drawRoundRect(RectF(M, 410f, M + 24, 434f), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor.toArgb() })
        val statusPaint = paint(Sage.muted, 30f, mono, 0.06f)
        canvas.drawText(status.uppercase(), M + 40, 433f, statusPaint)
        if (alert) {
            val x = M + 40 + statusPaint.measureText(status.uppercase()) + 28
            val p = paint(Sage.bg, 26f, monoBold, 0.08f)
            val box = RectF(x, 404f, x + p.measureText("ALERT") + 28, 440f)
            canvas.drawRoundRect(box, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Sage.alert.toArgb() })
            canvas.drawText("ALERT", x + 14, 432f, p)
        }

        var y = 540f
        for (line in lines) {
            canvas.drawText(line.label, M, y, paint(Sage.text, 38f, Typeface.DEFAULT))
            if (line.word) canvas.drawText(Words.chance(line.p), M, y + 46, paint(Sage.muted, 32f, Typeface.DEFAULT))
            val pct = paint(chanceColor(line.p), 64f, monoBold)
            val number = Words.pctShort(line.p)
            canvas.drawText(number, right - pct.measureText(number), y + 34, pct)
            val track = RectF(M, y + 72, right, y + 86)
            canvas.drawRoundRect(track, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Sage.line.toArgb() })
            val fill = RectF(M, y + 72, M + (right - M) * line.p.coerceIn(0.02, 1.0).toFloat(), y + 86)
            canvas.drawRoundRect(fill, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chanceColor(line.p).toArgb() })
            y += 140f
        }

        canvas.drawRect(M, 958f, right, 960f, Paint().apply { color = Sage.line.toArgb() })
        canvas.drawText("wysesage.xyz · forecast made ${Words.day(f.generated)}", M, 1006f, paint(Sage.faint, 28f, mono))
        canvas.drawText("A statistical forecast, not a warning service.", M, 1046f, paint(Sage.faint, 28f, mono))
        return bitmap
    }
}
