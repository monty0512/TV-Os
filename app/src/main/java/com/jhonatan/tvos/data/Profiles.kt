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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Profile(
    val id: String,
    val name: String,
    /** Índice en [com.jhonatan.tvos.ui.theme.ProfileColors]. */
    val color: Int,
    val settings: UserSettings = UserSettings(),
    /** Momento en que se cambió el fondo enviado desde el móvil; 0 = sin fondo. */
    val backgroundVersion: Long = 0,
)

data class ProfilesState(
    val profiles: List<Profile>,
    val activeId: String?,
    /** false hasta terminar la bienvenida (pantalla 7 del diseño). */
    val onboarded: Boolean,
) {
    val active: Profile? get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Perfiles y sus ajustes. Todo vive en el equipo; nada se sube a la nube. */
class ProfileStore(private val context: Context) {

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles_json")
        val ACTIVE = stringPreferencesKey("active_profile")
        val ONBOARDED = booleanPreferencesKey("onboarded")

        // Fase 1 (un solo juego de ajustes): se migran al primer perfil.
        val LEGACY_THEME = stringPreferencesKey("theme")
        val LEGACY_ACCENT = intPreferencesKey("accent")
        val LEGACY_DENSITY = stringPreferencesKey("density")
        val LEGACY_LARGE_TEXT = booleanPreferencesKey("large_text")
        val LEGACY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val LEGACY_ORDER = stringPreferencesKey("order")
        val LEGACY_HIDDEN = stringSetPreferencesKey("hidden")
    }

    val state: Flow<ProfilesState> = context.dataStore.data.map(::read)

    /** Cambia los ajustes del perfil activo. */
    suspend fun updateActive(transform: (UserSettings) -> UserSettings) = edit { s ->
        val id = s.active?.id ?: return@edit s
        s.copy(profiles = s.profiles.map { if (it.id == id) it.copy(settings = transform(it.settings)) else it })
    }

    /** Cambia los ajustes de todos los perfiles (bienvenida: favoritas, tema y tamaño iniciales). */
    suspend fun updateAll(transform: (UserSettings) -> UserSettings) = edit { s ->
        s.copy(profiles = s.profiles.map { it.copy(settings = transform(it.settings)) })
    }

    suspend fun setActive(id: String) = edit { it.copy(activeId = id) }

    suspend fun addProfile(name: String, color: Int) = edit { s ->
        // Un perfil nuevo parte de los ajustes del activo, para no empezar de cero.
        val base = s.active?.settings ?: UserSettings()
        val p = Profile(UUID.randomUUID().toString(), name.trim(), color, base)
        s.copy(profiles = s.profiles + p, activeId = s.activeId ?: p.id)
    }

    suspend fun renameProfile(id: String, name: String, color: Int) = edit { s ->
        s.copy(profiles = s.profiles.map { if (it.id == id) it.copy(name = name.trim(), color = color) else it })
    }

    /** version = 0 quita el fondo. */
    suspend fun setBackground(id: String, version: Long) = edit { s ->
        s.copy(profiles = s.profiles.map { if (it.id == id) it.copy(backgroundVersion = version) else it })
    }

    suspend fun removeProfile(id: String) = edit { s ->
        if (s.profiles.size <= 1) return@edit s
        val rest = s.profiles.filterNot { it.id == id }
        s.copy(profiles = rest, activeId = if (s.activeId == id) rest.first().id else s.activeId)
    }

    suspend fun completeOnboarding() = edit { it.copy(onboarded = true) }

    private suspend fun edit(transform: (ProfilesState) -> ProfilesState) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): ProfilesState {
        val json = p[Keys.PROFILES]
        if (json == null) {
            val legacy = readLegacy(p)
            val profiles = if (legacy != null) listOf(Profile("p1", "", 0, legacy)) else emptyList()
            return ProfilesState(profiles, profiles.firstOrNull()?.id, onboarded = false)
        }
        val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        val profiles = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Profile(
                id = o.optString("id"),
                name = o.optString("name"),
                color = o.optInt("color", 0),
                backgroundVersion = o.optLong("bgVersion", 0),
                settings = UserSettings.fromJson(o.optJSONObject("settings")),
            )
        }
        return ProfilesState(profiles, p[Keys.ACTIVE], p[Keys.ONBOARDED] ?: false)
    }

    private fun write(p: MutablePreferences, s: ProfilesState) {
        val arr = JSONArray()
        s.profiles.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("color", it.color)
                    .put("bgVersion", it.backgroundVersion)
                    .put("settings", it.settings.toJson()),
            )
        }
        p[Keys.PROFILES] = arr.toString()
        if (s.activeId != null) p[Keys.ACTIVE] = s.activeId else p.remove(Keys.ACTIVE)
        p[Keys.ONBOARDED] = s.onboarded
    }

    private fun readLegacy(p: Preferences): UserSettings? {
        if (p[Keys.LEGACY_THEME] == null && p[Keys.LEGACY_ORDER] == null) return null
        return UserSettings(
            theme = p[Keys.LEGACY_THEME].toEnum(ThemeMode.DARK),
            accent = p[Keys.LEGACY_ACCENT] ?: 0,
            density = p[Keys.LEGACY_DENSITY].toEnum(Density.COMPACT),
            largeText = p[Keys.LEGACY_LARGE_TEXT] ?: false,
            highContrast = p[Keys.LEGACY_HIGH_CONTRAST] ?: false,
            order = p[Keys.LEGACY_ORDER]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
            hidden = p[Keys.LEGACY_HIDDEN] ?: emptySet(),
        )
    }
}
