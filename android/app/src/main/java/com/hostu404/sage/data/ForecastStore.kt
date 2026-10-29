package com.hostu404.sage.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the forecast comes from: SAGE's GitHub Pages site, which the SAGE
 * project's scheduled run updates. The app never sends anything there; it
 * only downloads this one public file.
 */
const val FORECAST_URL = "https://hostu404.github.io/SAGE/forecasts/latest.json"

/** SAGE's code and data, for "View source". */
const val REPO_URL = "https://github.com/Hostu404/SAGE"

sealed interface Refresh {
    /** A newer forecast; [old] is the one it replaced, if there was one. */
    data class Updated(val old: Forecast?, val new: Forecast) : Refresh
    data object Unchanged : Refresh
    data class Failed(val reason: String) : Refresh
}

/**
 * The forecast on the phone: the last one downloaded, or the copy that came
 * with the app, whichever is newer. Works offline from the first launch.
 */
class ForecastStore(private val context: Context, private val url: String = FORECAST_URL) {
    private val file = File(context.filesDir, "latest.json")
    private val meta = context.getSharedPreferences("forecast_store", Context.MODE_PRIVATE)

    fun load(): Forecast {
        val bundled = Forecast.parse(bundledText())
        val saved = if (file.exists()) runCatching { Forecast.parse(file.readText()) }.getOrNull() else null
        return if (saved != null && saved.generated >= bundled.generated) saved else bundled
    }

    /** Downloads the forecast if it changed since last time. Safe to call from a background thread only. */
    fun refresh(): Refresh {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Refresh.Failed(e.message ?: "no connection")
        }
        return try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "SAGE-Android")
            meta.getString(ETAG, null)?.let { connection.setRequestProperty("If-None-Match", it) }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Refresh.Unchanged
                HttpURLConnection.HTTP_OK -> {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    // Only keep a file that reads as a forecast.
                    val new = runCatching { Forecast.parse(text) }.getOrNull()
                        ?: return Refresh.Failed("the downloaded file isn't a forecast")
                    val old = load()
                    if (new.generated <= old.generated) return Refresh.Unchanged
                    val tmp = File(context.filesDir, "latest.json.tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(file)) return Refresh.Failed("couldn't save the forecast")
                    meta.edit().putString(ETAG, connection.getHeaderField("ETag")).apply()
                    Refresh.Updated(old, new)
                }
                else -> Refresh.Failed("the server answered $code")
            }
        } catch (e: IOException) {
            Refresh.Failed(e.message ?: "no connection")
        } finally {
            connection.disconnect()
        }
    }

    private fun bundledText(): String = context.assets.open("latest.json").bufferedReader().use { it.readText() }

    private companion object {
        const val ETAG = "etag"
    }
}

/** What you've chosen in the app. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sage", Context.MODE_PRIVATE)

    /** Countries you've starred for your home screen. */
    var followed: Set<Int>
        get() = sp.getStringSet(FOLLOWED, null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet(FOLLOWED, value.map { it.toString() }.toSet()).apply()

    /** The country you were last found to be in, as a two-letter code. */
    var here: String?
        get() = sp.getString(HERE, null)
        set(value) = sp.edit().putString(HERE, value).apply()

    /** Your watchlist as you last marked it seen. */
    var seen: Seen?
        get() = Seen.parse(sp.getString(SEEN, null))
        set(value) = sp.edit().putString(SEEN, value?.toJson()).apply()

    private companion object {
        const val FOLLOWED = "followed"
        const val HERE = "here"
        const val SEEN = "seen"
    }
}
