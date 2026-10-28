package com.jhonatan.tvos.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class WeatherNow(val tempC: Int, val code: Int, val city: String)

enum class Sky { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

/**
 * Clima por IP: ubicación aproximada con ipapi.co (respaldo: ipwho.is) y temperatura con Open-Meteo.
 * Ninguno pide clave. La ubicación se guarda 24 h; si todo falla, el Inicio no muestra el clima.
 */
object Weather {
    private const val PREFS = "weather"
    private const val LOCATION_TTL_MS = 24 * 60 * 60 * 1000L

    suspend fun fetch(context: Context): WeatherNow? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon, city) = location(context) ?: return@runCatching null
            val json = JSONObject(
                get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"),
            ).getJSONObject("current")
            WeatherNow(json.getDouble("temperature_2m").roundToInt(), json.getInt("weather_code"), city)
        }.onFailure { Log.w("TvOs", "clima: ${it.javaClass.simpleName} ${it.message}") }.getOrNull()
    }

    /** Códigos WMO de Open-Meteo agrupados. */
    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2, 3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> if (code >= 51) Sky.RAIN else Sky.CLOUDY
    }

    private fun location(context: Context): Triple<Double, Double, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("at", 0) < LOCATION_TTL_MS && prefs.contains("lat")) {
            return Triple(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble(), prefs.getString("city", "") ?: "")
        }
        val found = listOf("https://ipapi.co/json/", "https://ipwho.is/").firstNotNullOfOrNull { url ->
            runCatching {
                val o = JSONObject(get(url))
                Triple(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city"))
            }.getOrNull()
        } ?: return null
        prefs.edit()
            .putFloat("lat", found.first.toFloat())
            .putFloat("lon", found.second.toFloat())
            .putString("city", found.third)
            .putLong("at", System.currentTimeMillis())
            .apply()
        return found
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "TV-Os")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
