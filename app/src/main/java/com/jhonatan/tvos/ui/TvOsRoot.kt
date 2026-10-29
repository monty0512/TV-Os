package com.jhonatan.tvos.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.KeyEvent as NativeKeyEvent
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.jhonatan.tvos.data.AppRepository
import com.jhonatan.tvos.data.Backgrounds
import com.jhonatan.tvos.data.PhotoKind
import com.jhonatan.tvos.data.Images
import com.jhonatan.tvos.data.Profile
import com.jhonatan.tvos.data.ShelfItem
import com.jhonatan.tvos.data.TvContentRepository
import com.jhonatan.tvos.data.Weather
import com.jhonatan.tvos.data.WeatherNow
import com.jhonatan.tvos.data.ProfileStore
import com.jhonatan.tvos.data.ProfilesState
import com.jhonatan.tvos.data.TvApp
import com.jhonatan.tvos.data.UserSettings
import com.jhonatan.tvos.perf.Perf
import com.jhonatan.tvos.ui.controlcenter.ControlCenter
import com.jhonatan.tvos.ui.edit.EditScreen
import com.jhonatan.tvos.ui.home.HomeScreen
import com.jhonatan.tvos.ui.onboarding.OnboardingScreen
import com.jhonatan.tvos.ui.profiles.ProfilesScreen
import com.jhonatan.tvos.ui.settings.AppearanceScreen
import com.jhonatan.tvos.ui.wallpaper.PhoneWallpaperScreen
import com.jhonatan.tvos.ui.theme.LocalReducedEffects
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.tvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class Screen { ONBOARDING, PROFILES, HOME, EDIT, APPEARANCE, PHONE_WALLPAPER }

private const val LOG_TAG = "TvOs"
private const val MAX_ROW_ITEMS = 10

@Composable
fun TvOsRoot(
    homeEvents: Int,
    controlCenterEvents: Int,
    controlCenterToggles: Int,
    foreground: Boolean,
    trimEvents: Int,
) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context.applicationContext) }
    val repo = remember { AppRepository(context.applicationContext) }
    val content = remember { TvContentRepository(context.applicationContext) }
    val reduced = remember { Perf.reducedEffects(context) }
    val scope = rememberCoroutineScope()

    val stateOrNull by store.state.collectAsState(initial = null)
    var allApps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var screen by rememberSaveable { mutableStateOf<Screen?>(null) }
    var controlCenterOpen by rememberSaveable { mutableStateOf(false) }
    var editReturn by rememberSaveable { mutableStateOf(Screen.HOME) }
    var editInitial by rememberSaveable { mutableStateOf<String?>(null) }
    var photoKind by rememberSaveable { mutableStateOf(PhotoKind.BACKGROUND) }
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var shelfContent by remember { mutableStateOf<Map<String, List<ShelfItem>>>(emptyMap()) }
    var watchNext by remember { mutableStateOf<List<ShelfItem>>(emptyList()) }
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    var weatherKey by remember { mutableIntStateOf(0) }
    var welcome by remember { mutableStateOf<Profile?>(null) }
    var profileBackground by remember { mutableStateOf<ImageBitmap?>(null) }
    val latestState by rememberUpdatedState(stateOrNull)

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
    // Al encender la TV (salir del reposo): "¿Quién está viendo?" si hay más de un perfil.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val s = latestState ?: return
                if (s.onboarded && s.profiles.size > 1) {
                    controlCenterOpen = false
                    screen = Screen.PROFILES
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val isForeground by rememberUpdatedState(foreground)
    LaunchedEffect(reloadKey) {
        val loaded = repo.loadApps()
        allApps = if (isForeground || !reduced) loaded else loaded.withoutBanners()
        loading = false
    }
    // Equipo low-RAM: los banners se sueltan solo cuando Android pide memoria con TV-Os en
    // segundo plano (no en cada cambio de app: recargarlos al volver retrasaba abrir la siguiente).
    // Se recargan al volver al Inicio; mientras tanto se ve el color de cada app.
    LaunchedEffect(trimEvents) {
        if (trimEvents > 0) Images.clear()
        if (trimEvents > 0 && reduced && !isForeground) allApps = allApps.withoutBanners()
    }
    // Contenido de las apps (Top Shelf y "Continuar viendo"): se relee cada vez que se vuelve al Inicio.
    LaunchedEffect(foreground, reloadKey) {
        if (!foreground) return@LaunchedEffect
        shelfContent = content.previewByPackage()
        watchNext = content.watchNext()
    }
    // Clima: cada 30 minutos mientras TV-Os está en pantalla, y al cambiar la ciudad.
    LaunchedEffect(foreground, weatherKey) {
        while (foreground) {
            Weather.fetch(context)?.let { weather = it }
            delay(30 * 60_000L)
        }
    }
    LaunchedEffect(foreground) {
        if (foreground && allApps.any { it.banner == null }) reloadKey++
    }
    // Hora para el tema automático.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L)
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }

    val state = stateOrNull
    if (state == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)))
        return
    }

    // Primera pantalla al arrancar.
    LaunchedEffect(Unit) {
        if (screen == null) screen = initialScreen(state)
    }
    // Botón Inicio del mando: vuelve al Inicio (salvo durante la bienvenida).
    LaunchedEffect(homeEvents) {
        if (homeEvents > 0 && screen != Screen.ONBOARDING && state.onboarded) {
            controlCenterOpen = false
            screen = Screen.HOME
        }
    }
    // Pulsación larga de Inicio (si la TV la envía como ALL_APPS): Centro de control.
    LaunchedEffect(controlCenterEvents) {
        if (controlCenterEvents > 0 && state.onboarded) controlCenterOpen = true
    }
    // ≡ con TV-Os ya en pantalla: abre o cierra.
    LaunchedEffect(controlCenterToggles) {
        if (controlCenterToggles > 0 && state.onboarded) controlCenterOpen = !controlCenterOpen
    }

    val current = screen ?: initialScreen(state)
    val settings = state.active?.settings ?: UserSettings()
    val orderIndex = remember(settings.order) { settings.order.withIndex().associate { it.value to it.index } }
    val visible = remember(allApps, settings.order, settings.hidden) {
        allApps
            .filter { it.packageName !in settings.hidden }
            .sortedBy { orderIndex[it.packageName] ?: Int.MAX_VALUE } // estable: el resto queda alfabético
    }
    val hiddenApps = remember(allApps, settings.hidden) { allApps.filter { it.packageName in settings.hidden } }
    // Contenido por app: primero lo que dejaste a medias en esa app, luego sus recomendaciones.
    val appContent = remember(watchNext, shelfContent) {
        val byApp = watchNext.groupBy { it.packageName }
        (byApp.keys + shelfContent.keys).associateWith { pkg ->
            (byApp[pkg].orEmpty() + shelfContent[pkg].orEmpty()).distinctBy { it.title }.take(MAX_ROW_ITEMS)
        }
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        scope.launch { store.updateActive(transform) }
    }

    // Fondo del perfil activo (enviado desde el móvil).
    val backgroundUri = state.active?.let { Backgrounds.uri(context, PhotoKind.BACKGROUND, it) }
    LaunchedEffect(backgroundUri) {
        profileBackground = backgroundUri?.let { Images.load(context, it, 1280, 720) }
    }

    val canOpenControlCenter = current == Screen.HOME || current == Screen.EDIT || current == Screen.APPEARANCE

    BackHandler(enabled = current == Screen.HOME || current == Screen.ONBOARDING) { /* Un launcher no se cierra con Atrás. */ }
    BackHandler(enabled = current == Screen.PROFILES) { screen = Screen.HOME }
    BackHandler(enabled = current == Screen.APPEARANCE) { screen = Screen.HOME }
    BackHandler(enabled = current == Screen.PHONE_WALLPAPER) { screen = Screen.APPEARANCE }
    BackHandler(enabled = current == Screen.EDIT) { screen = editReturn }
    BackHandler(enabled = controlCenterOpen) { controlCenterOpen = false }

    CompositionLocalProvider(
        LocalTv provides tvColors(settings, hour),
        LocalReducedEffects provides reduced,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Botón ≡ del mando: abre o cierra el Centro de control.
                    val n = event.nativeKeyEvent
                    // Diagnóstico: qué teclas del mando llegan a TV-Os (tools/adb-kit/logs.*).
                    if (n.action == NativeKeyEvent.ACTION_DOWN && n.repeatCount == 0) {
                        Log.d(LOG_TAG, "key ${NativeKeyEvent.keyCodeToString(n.keyCode)} (${n.keyCode})")
                    }
                    val isMenuKey = n.keyCode == NativeKeyEvent.KEYCODE_MENU || n.keyCode == NativeKeyEvent.KEYCODE_SETTINGS
                    if (!isMenuKey || !canOpenControlCenter) return@onPreviewKeyEvent false
                    if (n.action == NativeKeyEvent.ACTION_UP) controlCenterOpen = !controlCenterOpen
                    true
                },
        ) {
            when (current) {
                Screen.ONBOARDING -> OnboardingScreen(
                    state = state,
                    apps = allApps,
                    onAddProfile = { name, color -> scope.launch { store.addProfile(name, color) } },
                    onRenameUnnamed = { id, name, color -> scope.launch { store.renameProfile(id, name, color) } },
                    onFinish = { favorites, theme, density ->
                        scope.launch {
                            store.updateAll { s -> s.copy(order = favorites + (s.order - favorites.toSet()), theme = theme, density = density) }
                            store.completeOnboarding()
                            welcome = state.active
                            screen = Screen.HOME
                        }
                    },
                )

                Screen.PROFILES -> ProfilesScreen(
                    state = state,
                    onPick = { id ->
                        scope.launch { store.setActive(id) }
                        welcome = state.profiles.firstOrNull { it.id == id }
                        screen = Screen.HOME
                    },
                    onAdd = { name, color -> scope.launch { store.addProfile(name, color) } },
                    onRemove = { id ->
                        Backgrounds.deleteAll(context, id)
                        scope.launch { store.removeProfile(id) }
                    },
                )

                Screen.HOME -> HomeScreen(
                    apps = visible,
                    density = settings.density,
                    loading = loading,
                    homeEvents = homeEvents,
                    profileName = state.active?.name.orEmpty(),
                    weather = weather,
                    profileBackground = profileBackground,
                    appContent = appContent,
                    onLaunch = { app ->
                        Log.i(LOG_TAG, "launch ${app.packageName}")
                        repo.launchIntent(app.packageName)?.let { runCatching { context.startActivity(it) } }
                    },
                    onOpenProgram = { item ->
                        Log.i(LOG_TAG, "open program ${item.packageName} '${item.title}'")
                        val intent = TvContentRepository.openIntent(item) ?: repo.launchIntent(item.packageName)
                        intent?.let { runCatching { context.startActivity(it) } }
                    },
                    onEdit = { app ->
                        editInitial = app.packageName
                        editReturn = Screen.HOME
                        screen = Screen.EDIT
                    },
                    onPersonalize = { screen = Screen.APPEARANCE },
                    onOpenControlCenter = { controlCenterOpen = true },
                )

                Screen.EDIT -> EditScreen(
                    visible = visible,
                    hidden = hiddenApps,
                    density = settings.density,
                    initialSelected = editInitial,
                    onMove = { pkg, delta ->
                        // Como en Apple TV: la app se saca de su sitio y se inserta en el destino;
                        // el resto se reacomoda. ▲▼ mueven una fila entera (delta = ±columnas).
                        val list = visible.map { it.packageName }.toMutableList()
                        val i = list.indexOf(pkg)
                        val j = (i + delta).coerceIn(0, list.lastIndex)
                        if (i >= 0 && j != i) {
                            list.removeAt(i)
                            list.add(j, pkg)
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
                    onPhoneWallpaper = {
                        photoKind = PhotoKind.BACKGROUND
                        screen = Screen.PHONE_WALLPAPER
                    },
                    onPhoneAvatar = {
                        photoKind = PhotoKind.AVATAR
                        screen = Screen.PHONE_WALLPAPER
                    },
                    onWeatherChanged = { weatherKey++ },
                    onBack = { screen = Screen.HOME },
                )

                Screen.PHONE_WALLPAPER -> {
                    val profile = state.active
                    if (profile != null) {
                        val kind = photoKind
                        // Fondo y foto de perfil comparten pantalla; solo cambia dónde se guarda la versión.
                        suspend fun setVersion(id: String, version: Long) =
                            if (kind == PhotoKind.AVATAR) store.setAvatar(id, version) else store.setBackground(id, version)
                        PhoneWallpaperScreen(
                            kind = kind,
                            profileName = profile.name,
                            hasPhoto = Backgrounds.uri(context, kind, profile) != null,
                            onPhoto = { bytes ->
                                // Se llama desde el hilo del servidor: se guarda aquí y se avisa al perfil.
                                val id = profile.id
                                val ok = runCatching { Backgrounds.save(context, kind, id, bytes) }.getOrDefault(false)
                                if (ok) scope.launch { setVersion(id, System.currentTimeMillis()) }
                                ok
                            },
                            onRemove = {
                                Backgrounds.delete(context, kind, profile.id)
                                scope.launch { setVersion(profile.id, 0) }
                                screen = Screen.APPEARANCE
                            },
                            onBack = { screen = Screen.APPEARANCE },
                        )
                    }
                }
            }

            if (controlCenterOpen) {
                ControlCenter(
                    profile = state.active,
                    weather = weather,
                    onSwitchProfile = {
                        controlCenterOpen = false
                        screen = Screen.PROFILES
                    },
                    onClose = { controlCenterOpen = false },
                )
            }

            WelcomeOverlay(welcome, onDone = { welcome = null })
        }
    }
}

private fun initialScreen(s: ProfilesState): Screen = when {
    !s.onboarded || s.profiles.isEmpty() -> Screen.ONBOARDING
    s.profiles.size > 1 -> Screen.PROFILES
    else -> Screen.HOME
}

private fun List<TvApp>.withoutBanners() = map { it.copy(banner = null) }
