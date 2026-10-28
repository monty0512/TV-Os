package com.jhonatan.tvos.data

import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { DARK, LIGHT, AUTO }

enum class Density { LARGE, MEDIUM, COMPACT }

/** Ajustes de un perfil: cada persona ve TV-Os a su manera. */
data class UserSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0,
    val density: Density = Density.COMPACT,
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    /** Orden de las apps (nombres de paquete); la primera fila son las favoritas. Las que no estén aquí van al final. */
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("accent", accent)
        .put("density", density.name)
        .put("largeText", largeText)
        .put("highContrast", highContrast)
        .put("order", JSONArray(order))
        .put("hidden", JSONArray(hidden.toList()))

    companion object {
        fun fromJson(o: JSONObject?): UserSettings {
            if (o == null) return UserSettings()
            return UserSettings(
                theme = o.optString("theme").toEnum(ThemeMode.DARK),
                accent = o.optInt("accent", 0),
                density = o.optString("density").toEnum(Density.COMPACT),
                largeText = o.optBoolean("largeText", false),
                highContrast = o.optBoolean("highContrast", false),
                order = o.optJSONArray("order").toStringList(),
                hidden = o.optJSONArray("hidden").toStringList().toSet(),
            )
        }
    }
}

internal inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

internal fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }
