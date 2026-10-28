package com.jhonatan.tvos.ui.home

import android.text.format.DateFormat
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.jhonatan.tvos.data.Images
import com.jhonatan.tvos.data.ShelfItem
import com.jhonatan.tvos.data.WeatherNow
import com.jhonatan.tvos.ui.components.greeting
import com.jhonatan.tvos.ui.components.weatherText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.Density
import com.jhonatan.tvos.data.TvApp
import com.jhonatan.tvos.data.darken
import com.jhonatan.tvos.data.lighten
import com.jhonatan.tvos.ui.components.AppTile
import com.jhonatan.tvos.ui.components.remoteClickable
import com.jhonatan.tvos.ui.theme.LocalReducedEffects
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText
import kotlinx.coroutines.delay
import java.util.Date

/** Margen lateral de todo el Inicio. */
val SidePadding = 48.dp
private val DrawerHeaderHeight = 52.dp
private const val SHELF_ROTATE_MS = 7_000L
/**
 * Alto de la fila de favoritas además del tile: margen superior de la cuadrícula (18) + espacio y nombre
 * bajo el tile (~26). La segunda fila empieza más abajo (+12 de separación), así que queda oculta.
 */
private val FAVORITES_EXTRA = 48.dp
// Tamaño de decodificación (px) de la imagen del Top Shelf y de las tarjetas: suficiente a 1080p con poca RAM.
private const val HERO_W = 1280
private const val HERO_H = 720

data class TileSize(val columns: Int, val width: Dp, val height: Dp, val gap: Dp)

/** Columnas según Apariencia (5/6/7); el tile ocupa toda la celda en 16:9. */
fun tileSize(density: Density, screenWidth: Dp): TileSize {
    val (columns, gap) = when (density) {
        Density.LARGE -> 5 to 20.dp
        Density.MEDIUM -> 6 to 18.dp
        Density.COMPACT -> 7 to 16.dp
    }
    val width = (screenWidth - SidePadding * 2 - gap * (columns - 1)) / columns
    return TileSize(columns, width, width * 9 / 16, gap)
}

/**
 * Inicio al estilo Apple TV (pantallas 9 y 10 del diseño):
 * Top Shelf + primera fila de favoritas + "Continuar viendo"; al bajar, el Top Shelf se recoge y la
 * cuadrícula con todas las apps ocupa la pantalla.
 */
@Composable
fun HomeScreen(
    apps: List<TvApp>,
    density: Density,
    loading: Boolean,
    homeEvents: Int,
    profileName: String,
    weather: WeatherNow?,
    /** Fondo del perfil enviado desde el móvil (null = color de la app). */
    profileBackground: ImageBitmap?,
    /** Contenido de cada app ("Continuar viendo" primero, luego sus recomendaciones). */
    appContent: Map<String, List<ShelfItem>>,
    onLaunch: (TvApp) -> Unit,
    onOpenProgram: (ShelfItem) -> Unit,
    onEdit: (TvApp) -> Unit,
    onPersonalize: () -> Unit,
    onOpenControlCenter: () -> Unit,
) {
    val t = LocalTv.current
    val reduced = LocalReducedEffects.current
    val size = tileSize(density, LocalConfiguration.current.screenWidthDp.dp)
    var focusedIdx by remember { mutableIntStateOf(0) }
    val focusedApp = apps.getOrNull(focusedIdx)
    // Fuera de la primera fila = cajón desplegado.
    val inDrawer = focusedIdx >= size.columns

    // Top Shelf: carrusel con el contenido de la app enfocada, o su color y nombre si no publica nada.
    val shelfItems: List<ShelfItem> = focusedApp?.let { appContent[it.packageName] }.orEmpty()
    val shelfLabel = focusedApp?.label
    val shelfTarget = focusedApp?.let { if (t.isDark) darken(it.color, 0.6f) else lighten(it.color, 0.7f) } ?: t.tile
    val shelfColor by animateColorAsState(shelfTarget, tween(if (reduced) 0 else 300), label = "shelf")
    // Fuera del cajón, el Top Shelf ocupa toda la TV y solo queda visible la fila de favoritas abajo.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val favoritesArea = size.height + FAVORITES_EXTRA
    val headerHeight by animateDpAsState(
        targetValue = if (inDrawer) DrawerHeaderHeight else screenHeight - favoritesArea,
        animationSpec = tween(if (reduced) 140 else 260),
        label = "header",
    )

    val firstFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    // Al pulsar Inicio (o al cargar): arriba del todo y foco en la primera favorita.
    LaunchedEffect(homeEvents, apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        focusedIdx = 0
        gridState.scrollToItem(0)
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    val favorites = apps.take(size.columns)
    val rest = apps.drop(size.columns)

    Box(Modifier.fillMaxSize().background(t.bg)) {
        // En el cajón, el fondo del perfil queda detrás de las apps con un velo fuerte.
        if (inDrawer && profileBackground != null) {
            Image(profileBackground, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(t.bg.copy(alpha = 0.82f)))
        }
        // Capa de fondo: carrusel a pantalla completa (imagen o color de la app) detrás de la fila de favoritas.
        if (!inDrawer) {
            TopShelf(
                appLabel = shelfLabel,
                items = shelfItems,
                background = shelfColor,
                profileBackground = profileBackground,
                bottomInset = favoritesArea,
                profileName = profileName,
                weather = weather,
                onOpenProgram = onOpenProgram,
                onOpenControlCenter = onOpenControlCenter,
            )
        }
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .then(if (inDrawer) Modifier.background(drawerHeaderColor(t.isDark)) else Modifier),
            ) {
                if (inDrawer) {
                    DrawerHeader(focusedApp?.label ?: stringResource(R.string.personalize), onOpenControlCenter)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(size.columns),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (inDrawer && profileBackground == null) Modifier.background(t.bg) else Modifier),
                contentPadding = PaddingValues(start = SidePadding, end = SidePadding, top = 18.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(size.gap),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(favorites, key = { _, app -> app.packageName }) { i, app ->
                    AppTile(
                        app = app,
                        width = size.width,
                        height = size.height,
                        onClick = { onLaunch(app) },
                        onLongClick = { onEdit(app) },
                        onFocused = { focusedIdx = i },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
                itemsIndexed(rest, key = { _, app -> app.packageName }) { i, app ->
                    AppTile(
                        app = app,
                        width = size.width,
                        height = size.height,
                        onClick = { onLaunch(app) },
                        onLongClick = { onEdit(app) },
                        onFocused = { focusedIdx = size.columns + i },
                    )
                }
                item(key = "personalize") {
                    PersonalizeTile(size, onClick = onPersonalize, onFocused = { focusedIdx = apps.size })
                }
            }
            if (loading && apps.isEmpty()) {
                TvText(stringResource(R.string.loading_apps), 16.sp, color = t.fgMuted, modifier = Modifier.padding(start = SidePadding))
            }
        }
    }
}

private fun drawerHeaderColor(dark: Boolean) = if (dark) Color(0xFF141416) else Color(0xFFE5E5EA)

/**
 * Top Shelf en carrusel: muestra el contenido de la app enfocada ("Continuar viendo" primero) y rota solo.
 * ▲ desde la primera fila entra al carrusel: ◀ ▶ cambian de elemento y OK lo abre en su app.
 * Si la app no publica nada: su color y su nombre grande.
 */
@Composable
private fun TopShelf(
    appLabel: String?,
    items: List<ShelfItem>,
    background: Color,
    profileBackground: ImageBitmap?,
    /** Alto reservado abajo para la fila de favoritas (el texto del carrusel queda encima). */
    bottomInset: Dp,
    profileName: String,
    weather: WeatherNow?,
    onOpenProgram: (ShelfItem) -> Unit,
    onOpenControlCenter: () -> Unit,
) {
    val t = LocalTv.current
    val context = LocalContext.current
    var index by remember(items) { mutableIntStateOf(0) }
    var carouselFocused by remember { mutableStateOf(false) }
    // Rota solo mientras no se está navegando el carrusel.
    LaunchedEffect(items, carouselFocused) {
        while (items.size > 1 && !carouselFocused) {
            delay(SHELF_ROTATE_MS)
            index = (index + 1) % items.size
        }
    }
    val item = items.getOrNull(index)
    var hero by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item?.imageUri) {
        hero = item?.imageUri?.let { Images.load(context, it, HERO_W, HERO_H) }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        // Imagen del contenido de la app; si no hay, el fondo del perfil; si tampoco, el color de la app.
        val image = (if (item != null) hero else null) ?: profileBackground
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Velo para que el texto se lea sobre cualquier imagen, más oscuro detrás de la fila de favoritas.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(bottomInset)
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
        }
        val onImage = image != null
        val fg = if (onImage) Color.White else t.fg
        val muted = if (onImage) Color(0xFFE5E5EA) else t.fgMuted

        Box(Modifier.fillMaxSize().padding(start = SidePadding, end = SidePadding, top = 22.dp, bottom = bottomInset + 12.dp)) {
            TvText(
                text = greeting(profileName),
                size = 18.sp,
                weight = FontWeight.SemiBold,
                color = fg,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Row(
                Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (weather != null) TvText(weatherText(weather), 15.sp, color = muted)
                ControlCenterButton(onOpenControlCenter)
                Clock(color = fg)
            }
            if (item != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.62f)
                        .onPreviewKeyEvent { event ->
                            val n = event.nativeKeyEvent
                            val delta = when (n.keyCode) {
                                NativeKeyEvent.KEYCODE_DPAD_LEFT -> -1
                                NativeKeyEvent.KEYCODE_DPAD_RIGHT -> 1
                                else -> 0
                            }
                            if (delta == 0 || items.size < 2) return@onPreviewKeyEvent false
                            if (n.action == NativeKeyEvent.ACTION_DOWN) index = (index + delta + items.size) % items.size
                            true
                        }
                        .remoteClickable(onClick = { onOpenProgram(item) }, onFocus = { carouselFocused = it })
                        .padding(10.dp),
                ) {
                    TvText(appLabel?.uppercase() ?: "", 12.sp, weight = FontWeight.SemiBold, color = muted)
                    Spacer(Modifier.height(4.dp))
                    TvText(item.title, 48.sp, weight = FontWeight.Bold, color = fg, maxLines = 2)
                    item.subtitle?.let {
                        Spacer(Modifier.height(4.dp))
                        TvText(it, 15.sp, color = muted, maxLines = 2)
                    }
                    if (item.progress != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(220.dp).height(4.dp).background(muted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(t.accent, RoundedCornerShape(2.dp)))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        items.indices.forEach { i ->
                            Box(
                                Modifier
                                    .size(if (i == index) 18.dp else 6.dp, 6.dp)
                                    .background(if (i == index) fg else muted.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                            )
                        }
                        if (carouselFocused) {
                            Spacer(Modifier.width(10.dp))
                            TvText(stringResource(R.string.shelf_carousel_hint), 13.sp, color = muted)
                        }
                    }
                }
            } else {
                Column(Modifier.align(Alignment.BottomStart)) {
                    TvText(stringResource(R.string.shelf_no_channel), 13.sp, color = muted)
                    Spacer(Modifier.height(4.dp))
                    TvText(appLabel ?: stringResource(R.string.loading_apps), 64.sp, weight = FontWeight.Bold, color = fg)
                    Spacer(Modifier.height(4.dp))
                    TvText(stringResource(R.string.shelf_hint), 13.sp, color = muted)
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(focusedLabel: String, onOpenControlCenter: () -> Unit) {
    val t = LocalTv.current
    Row(Modifier.fillMaxSize().padding(horizontal = SidePadding), verticalAlignment = Alignment.CenterVertically) {
        TvText(stringResource(R.string.all_apps), 12.sp, weight = FontWeight.SemiBold, color = t.fgMuted)
        Spacer(Modifier.size(14.dp))
        TvText(focusedLabel, 18.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TvText(stringResource(R.string.drawer_hint), 13.sp, color = t.fgMuted)
        Spacer(Modifier.size(18.dp))
        ControlCenterButton(onOpenControlCenter)
        Spacer(Modifier.size(16.dp))
        Clock()
    }
}

@Composable
private fun PersonalizeTile(size: TileSize, onClick: () -> Unit, onFocused: () -> Unit) {
    val t = LocalTv.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(size.width, size.height)
            .background(if (focused) t.tileFocused else t.tile, shape)
            .then(if (focused) Modifier.border(t.borderWidth, t.focusBorder, shape) else Modifier)
            .remoteClickable(
                onClick = onClick,
                onFocus = {
                    focused = it
                    if (it) onFocused()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        TvText(
            text = stringResource(R.string.personalize),
            size = 13.sp,
            weight = FontWeight.SemiBold,
            color = if (focused) t.tileFocusedFg else t.fg,
        )
    }
}

/** Abre el Centro de control (▲ desde la primera fila). Funciona con cualquier mando. */
@Composable
private fun ControlCenterButton(onClick: () -> Unit) {
    val t = LocalTv.current
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) t.tileFocusedFg else t.fg
    Box(
        Modifier
            .size(40.dp)
            .background(if (focused) t.tileFocused else t.tile.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .then(if (focused) Modifier.border(t.borderWidth, t.focusBorder, RoundedCornerShape(20.dp)) else Modifier)
            .semantics { contentDescription = "Control center" }
            .remoteClickable(onClick = onClick, onFocus = { focused = it }),
        contentAlignment = Alignment.Center,
    ) {
        // Icono de deslizadores dibujado con cajas (sin depender de fuentes con símbolos).
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(18.dp, 12.dp, 16.dp).forEach { w ->
                Box(Modifier.size(w, 2.dp).background(fg, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier, color: Color = LocalTv.current.fg) {
    val context = LocalContext.current
    val format = remember { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    TvText(format.format(Date(now)), 22.sp, modifier = modifier, weight = FontWeight.Medium, color = color)
}
