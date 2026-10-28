package com.jhonatan.tvos.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT, AUTO }

enum class Density { LARGE, COMPACT }

data class UserSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0,
    val density: Density = Density.COMPACT,
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    /** Orden de la fila de inicio (nombres de paquete). Las apps que no estén aquí van al final. */
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = intPreferencesKey("accent")
        val DENSITY = stringPreferencesKey("density")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val ORDER = stringPreferencesKey("order")
        val HIDDEN = stringSetPreferencesKey("hidden")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map(::read)

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences) = UserSettings(
        theme = p[Keys.THEME].toEnum(ThemeMode.DARK),
        accent = p[Keys.ACCENT] ?: 0,
        density = p[Keys.DENSITY].toEnum(Density.COMPACT),
        largeText = p[Keys.LARGE_TEXT] ?: false,
        highContrast = p[Keys.HIGH_CONTRAST] ?: false,
        order = p[Keys.ORDER]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
        hidden = p[Keys.HIDDEN] ?: emptySet(),
    )

    private fun write(p: MutablePreferences, s: UserSettings) {
        p[Keys.THEME] = s.theme.name
        p[Keys.ACCENT] = s.accent
        p[Keys.DENSITY] = s.density.name
        p[Keys.LARGE_TEXT] = s.largeText
        p[Keys.HIGH_CONTRAST] = s.highContrast
        p[Keys.ORDER] = s.order.joinToString("\n")
        p[Keys.HIDDEN] = s.hidden
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
