package com.hostu404.sage.data

import android.content.Context
import android.telephony.TelephonyManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Which country you're in, as a two-letter code ("FR"): from your internet
 * connection, or failing that from the phone's mobile network, SIM or region
 * setting. None of it needs a permission, and only the country is kept.
 */
object WhereYouAre {
    /** Cloudflare's connection check: free, no key, and it answers with the country as "loc=FR". */
    const val LOOKUP_URL = "https://www.cloudflare.com/cdn-cgi/trace"

    /** From the internet connection; null when offline or the answer isn't a country. Background thread only. */
    fun fromConnection(): String? {
        val connection = try {
            URL(LOOKUP_URL).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return null
        }
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "SAGE-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else parse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** "loc=FR" -> "FR". "XX" (unknown) and "T1" (Tor) aren't countries. */
    fun parse(trace: String): String? = trace.lineSequence()
        .firstOrNull { it.startsWith("loc=") }
        ?.removePrefix("loc=")?.trim()?.uppercase()
        ?.takeIf { it.length == 2 && it.all(Char::isLetter) && it != "XX" && it != "T1" }

    /** From the phone itself, with no network needed. */
    fun fromPhone(context: Context): String? {
        val phone = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(phone?.networkCountryIso, phone?.simCountryIso, Locale.getDefault().country)
            .map { it.uppercase() }
            .firstOrNull { it.length == 2 }
    }

    /** "FR" -> "France", for countries SAGE has nothing on. */
    fun name(iso2: String): String =
        Locale("", iso2).getDisplayCountry(Locale.UK).ifEmpty { iso2 }

    /** "FR" -> "FRA", as in SAGE's file. Kosovo has no official code; SAGE uses XKX. */
    fun iso3(iso2: String): String? = if (iso2 == "XK") "XKX" else
        runCatching { Locale("", iso2).isO3Country }.getOrNull()?.ifEmpty { null }
}
