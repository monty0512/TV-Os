package com.jhonatan.tvos.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.jhonatan.tvos.data.AppRepository
import com.jhonatan.tvos.data.SettingsStore
import com.jhonatan.tvos.data.TvApp
import com.jhonatan.tvos.data.UserSettings
import com.jhonatan.tvos.perf.Perf
import com.jhonatan.tvos.ui.edit.EditScreen
import com.jhonatan.tvos.ui.home.HomeScreen
import com.jhonatan.tvos.ui.settings.AppearanceScreen
import com.jhonatan.tvos.ui.theme.LocalReducedEffects
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.tvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class Screen { HOME, EDIT, APPEARANCE }

@Composable
fun TvOsRoot(homeEvents: Int, foreground: Boolean) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val repo = remember { AppRepository(context.applicationContext) }
    val reduced = remember { Perf.reducedEffects(context) }
    val scope = rememberCoroutineScope()

    val settingsOrNull by store.settings.collectAsState(initial = null)
    var allApps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var editReturn by rememberSaveable { mutableStateOf(Screen.HOME) }
    var editInitial by rememberSaveable { mutableStateOf<String?>(null) }
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }

    // Recarga la lista cuando se instala, actualiza o desinstala una app.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                reloadKey++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val isForeground by rememberUpdatedState(foreground)
    LaunchedEffect(reloadKey) {
        val loaded = repo.loadApps()
        allApps = if (isForeground || !reduced) loaded else loaded.withoutBanners()
        loading = false
    }
    // Equipo low-RAM: al abrir otra app se sueltan los banners para dejarle memoria,
    // y se recargan al volver al Inicio (mientras tanto se ve el color de cada app).
    LaunchedEffect(foreground) {
        if (!reduced) return@LaunchedEffect
        if (!foreground) {
            allApps = allApps.withoutBanners()
            Runtime.getRuntime().gc()
        } else if (allApps.any { it.banner == null }) {
            reloadKey++
        }
    }
    // Hora para el tema automático.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L)
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }
    // Botón Inicio del mando: vuelve siempre al Inicio.
    LaunchedEffect(homeEvents) { screen = Screen.HOME }

    val settings = settingsOrNull
    if (settings == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)))
        return
    }

    val orderIndex = remember(settings.order) { settings.order.withIndex().associate { it.value to it.index } }
    val visible = remember(allApps, settings.order, settings.hidden) {
        allApps
            .filter { it.packageName !in settings.hidden }
            .sortedBy { orderIndex[it.packageName] ?: Int.MAX_VALUE } // estable: el resto queda alfabético
    }
    val hiddenApps = remember(allApps, settings.hidden) { allApps.filter { it.packageName in settings.hidden } }

    fun update(transform: (UserSettings) -> UserSettings) {
        scope.launch { store.update(transform) }
    }

    BackHandler(enabled = screen == Screen.HOME) { /* Un launcher no se cierra con Atrás. */ }
    BackHandler(enabled = screen == Screen.APPEARANCE) { screen = Screen.HOME }
    BackHandler(enabled = screen == Screen.EDIT) { screen = editReturn }

    CompositionLocalProvider(
        LocalTv provides tvColors(settings, hour),
        LocalReducedEffects provides reduced,
    ) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                apps = visible,
                density = settings.density,
                loading = loading,
                homeEvents = homeEvents,
                onLaunch = { app ->
                    repo.launchIntent(app.packageName)?.let { runCatching { context.startActivity(it) } }
                },
                onEdit = { app ->
                    editInitial = app.packageName
                    editReturn = Screen.HOME
                    screen = Screen.EDIT
                },
                onPersonalize = { screen = Screen.APPEARANCE },
            )

            Screen.EDIT -> EditScreen(
                visible = visible,
                hidden = hiddenApps,
                initialSelected = editInitial,
                onMove = { pkg, delta ->
                    val list = visible.map { it.packageName }.toMutableList()
                    val i = list.indexOf(pkg)
                    val j = i + delta
                    if (i >= 0 && j in list.indices) {
                        list[i] = list[j].also { list[j] = list[i] }
                        update { it.copy(order = list) }
                    }
                },
                onHide = { pkg -> update { it.copy(hidden = it.hidden + pkg, order = it.order - pkg) } },
                onRestore = { pkg -> update { it.copy(hidden = it.hidden - pkg) } },
                onDone = { screen = editReturn },
            )

            Screen.APPEARANCE -> AppearanceScreen(
                settings = settings,
                onChange = ::update,
                onReorder = {
                    editInitial = null
                    editReturn = Screen.APPEARANCE
                    screen = Screen.EDIT
                },
                onBack = { screen = Screen.HOME },
            )
        }
    }
}

private fun List<TvApp>.withoutBanners() = map { it.copy(banner = null) }
