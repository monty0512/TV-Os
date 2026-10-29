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

/** Resultado de buscar una ciudad (geocodificación de Open-Meteo). */
data class City(val name: String, val label: String, val lat: Double, val lon: Double)

enum class Sky { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

/**
 * Clima por IP: ubicación aproximada con ipapi.co (respaldo: ipwho.is) y temperatura con Open-Meteo.
 * Ninguno pide clave. La ubicación se guarda 24 h; si todo falla, el Inicio no muestra el clima.
 * Una ciudad elegida a mano en Apariencia tiene prioridad sobre la detectada por IP.
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
        }.onFailure {
            val internet = context.checkSelfPermission(android.Manifest.permission.INTERNET) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.w("TvOs", "clima: ${it.javaClass.simpleName} ${it.message} · permiso INTERNET=$internet")
        }.getOrNull()
    }

    /** Ciudad elegida a mano, o null si es automática. */
    fun manualCity(context: Context): String? =
        prefs(context).getString("manual_city", null)

    /** Última ciudad detectada por IP (para mostrarla en Apariencia). */
    fun autoCity(context: Context): String = prefs(context).getString("city", "") ?: ""

    fun setManual(context: Context, city: City) {
        prefs(context).edit()
            .putString("manual_city", city.name)
            .putFloat("manual_lat", city.lat.toFloat())
            .putFloat("manual_lon", city.lon.toFloat())
            .apply()
    }

    fun clearManual(context: Context) {
        prefs(context).edit().remove("manual_city").remove("manual_lat").remove("manual_lon").apply()
    }

    /** Busca una ciudad por nombre; null si no hay resultados o falla la red. */
    suspend fun search(name: String, language: String): City? = withContext(Dispatchers.IO) {
        runCatching {
            val q = java.net.URLEncoder.encode(name.trim(), "UTF-8")
            val results = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=$language&format=json"))
                .optJSONArray("results")
            val o = results?.optJSONObject(0) ?: return@runCatching null
            val city = o.getString("name")
            // admin1 y country se omiten si no existen.
            val label = listOf(city, o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            City(city, label, o.getDouble("latitude"), o.getDouble("longitude"))
        }.onFailure { Log.w("TvOs", "clima: búsqueda de ciudad falló: ${it.javaClass.simpleName} ${it.message}") }
            .getOrNull()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        val prefs = prefs(context)
        prefs.getString("manual_city", null)?.let { city ->
            return Triple(prefs.getFloat("manual_lat", 0f).toDouble(), prefs.getFloat("manual_lon", 0f).toDouble(), city)
        }
        if (System.currentTimeMillis() - prefs.getLong("at", 0) < LOCATION_TTL_MS && prefs.contains("lat")) {
            return Triple(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble(), prefs.getString("city", "") ?: "")
        }
        val found = listOf("https://ipapi.co/json/", "https://ipwho.is/").firstNotNullOfOrNull { url ->
            runCatching {
                val o = JSONObject(get(url))
                Triple(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city"))
            }.onFailure { Log.w("TvOs", "clima: ubicación por IP falló en $url: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull()
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
