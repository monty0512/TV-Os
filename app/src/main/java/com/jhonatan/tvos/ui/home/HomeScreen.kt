package com.jhonatan.tvos.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
private val ShelfHeight = 250.dp
private val DrawerHeaderHeight = 52.dp
private const val SHELF_ROTATE_MS = 7_000L
// Tamaño de decodificación (px) de la imagen del Top Shelf y de las tarjetas: suficiente a 1080p con poca RAM.
private const val HERO_W = 960
private const val HERO_H = 540
private const val CARD_W = 480
private const val CARD_H = 270

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
    // Programa enfocado en la fila de contenido (null = el foco está en una app).
    var focusedProgram by remember { mutableStateOf<ShelfItem?>(null) }
    val focusedApp = apps.getOrNull(focusedIdx)
    // La fila de contenido pertenece a la última app enfocada: no cambia al bajar a ella.
    var rowOwner by remember { mutableStateOf<String?>(null) }
    val ownerApp = apps.firstOrNull { it.packageName == rowOwner } ?: apps.firstOrNull()
    val rowItems = ownerApp?.let { appContent[it.packageName] }.orEmpty()
    // Fuera de la primera fila y de la fila de contenido = cajón desplegado.
    val inDrawer = focusedProgram == null && focusedIdx >= size.columns

    // Top Shelf: el programa enfocado, o el contenido de la app enfocada (rota), o su color y nombre.
    val shelfItems: List<ShelfItem> = focusedProgram?.let { listOf(it) }
        ?: focusedApp?.let { appContent[it.packageName] }.orEmpty()
    val colorSource = if (focusedProgram != null) ownerApp else focusedApp
    val shelfLabel = colorSource?.label
    val shelfTarget = colorSource?.let { if (t.isDark) darken(it.color, 0.6f) else lighten(it.color, 0.7f) } ?: t.tile
    val shelfColor by animateColorAsState(shelfTarget, tween(if (reduced) 0 else 300), label = "shelf")
    val headerHeight by animateDpAsState(
        targetValue = if (inDrawer) DrawerHeaderHeight else ShelfHeight,
        animationSpec = tween(if (reduced) 140 else 260),
        label = "header",
    )

    val firstFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    // Al pulsar Inicio (o al cargar): arriba del todo y foco en la primera favorita.
    LaunchedEffect(homeEvents, apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        focusedIdx = 0
        focusedProgram = null
        gridState.scrollToItem(0)
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    val favorites = apps.take(size.columns)
    val rest = apps.drop(size.columns)

    Column(Modifier.fillMaxSize().background(t.bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(if (inDrawer) drawerHeaderColor(t.isDark) else shelfColor),
        ) {
            if (inDrawer) {
                DrawerHeader(focusedApp?.label ?: stringResource(R.string.personalize), onOpenControlCenter)
            } else {
                TopShelf(shelfLabel, shelfItems, profileName, weather, onOpenControlCenter)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(size.columns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
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
                    onFocused = {
                        focusedIdx = i
                        focusedProgram = null
                        rowOwner = app.packageName
                    },
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            if (rowItems.isNotEmpty() && ownerApp != null) {
                item(key = "app_content", span = { GridItemSpan(maxLineSpan) }) {
                    AppContentRow(
                        appLabel = ownerApp.label,
                        items = rowItems,
                        cardWidth = size.width * 2 + size.gap,
                        onFocused = { focusedProgram = it },
                        onOpen = onOpenProgram,
                    )
                }
            }
            itemsIndexed(rest, key = { _, app -> app.packageName }) { i, app ->
                AppTile(
                    app = app,
                    width = size.width,
                    height = size.height,
                    onClick = { onLaunch(app) },
                    onLongClick = { onEdit(app) },
                    onFocused = {
                        focusedIdx = size.columns + i
                        focusedProgram = null
                        rowOwner = app.packageName
                    },
                )
            }
            item(key = "personalize") {
                PersonalizeTile(size, onClick = onPersonalize, onFocused = {
                    focusedIdx = apps.size
                    focusedProgram = null
                })
            }
        }
        if (loading && apps.isEmpty()) {
            TvText(stringResource(R.string.loading_apps), 16.sp, color = t.fgMuted, modifier = Modifier.padding(start = SidePadding))
        }
    }
}

private fun drawerHeaderColor(dark: Boolean) = if (dark) Color(0xFF141416) else Color(0xFFE5E5EA)

/**
 * Top Shelf: si la app publica contenido, se muestra a pantalla de cabecera y rota cada pocos segundos;
 * si no, el color de la app y su nombre grande.
 */
@Composable
private fun TopShelf(
    appLabel: String?,
    items: List<ShelfItem>,
    profileName: String,
    weather: WeatherNow?,
    onOpenControlCenter: () -> Unit,
) {
    val t = LocalTv.current
    val context = LocalContext.current
    var index by remember(items) { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        while (items.size > 1) {
            delay(SHELF_ROTATE_MS)
            index = (index + 1) % items.size
        }
    }
    val item = items.getOrNull(index)
    var hero by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item?.imageUri) {
        hero = item?.imageUri?.let { Images.load(context, it, HERO_W, HERO_H) }
    }

    Box(Modifier.fillMaxSize()) {
        val image = hero
        if (item != null && image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Velo para que el texto se lea sobre cualquier imagen.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        val onImage = item != null && image != null
        val fg = if (onImage) Color.White else t.fg
        val muted = if (onImage) Color(0xFFE5E5EA) else t.fgMuted

        Box(Modifier.fillMaxSize().padding(horizontal = SidePadding, vertical = 22.dp)) {
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
            Column(Modifier.align(Alignment.BottomStart)) {
                if (item != null) {
                    TvText(appLabel?.uppercase() ?: "", 12.sp, weight = FontWeight.SemiBold, color = muted)
                    Spacer(Modifier.height(4.dp))
                    TvText(item.title, 40.sp, weight = FontWeight.Bold, color = fg)
                    item.subtitle?.let {
                        Spacer(Modifier.height(4.dp))
                        TvText(it, 15.sp, color = muted, maxLines = 2, modifier = Modifier.fillMaxWidth(0.6f))
                    }
                } else {
                    TvText(stringResource(R.string.shelf_no_channel), 13.sp, color = muted)
                    Spacer(Modifier.height(4.dp))
                    TvText(appLabel ?: stringResource(R.string.loading_apps), 48.sp, weight = FontWeight.Bold, color = fg)
                    Spacer(Modifier.height(4.dp))
                    TvText(stringResource(R.string.shelf_hint), 13.sp, color = muted)
                }
            }
        }
    }
}

/** Contenido de la app enfocada: lo que dejaste a medias (con progreso) y sus recomendaciones. */
@Composable
private fun AppContentRow(
    appLabel: String,
    items: List<ShelfItem>,
    cardWidth: Dp,
    onFocused: (ShelfItem) -> Unit,
    onOpen: (ShelfItem) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvText(stringResource(R.string.app_content_title, appLabel), 16.sp, weight = FontWeight.SemiBold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ProgramCard(item, cardWidth, onFocused = { onFocused(item) }, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun ProgramCard(item: ShelfItem, width: Dp, onFocused: () -> Unit, onClick: () -> Unit) {
    val t = LocalTv.current
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    val height = width * 9 / 16
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.imageUri) { image = item.imageUri?.let { Images.load(context, it, CARD_W, CARD_H) } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(width, height)
                .graphicsLayer {
                    val s = if (focused) 1.06f else 1f
                    scaleX = s
                    scaleY = s
                    shape = cardShape
                    clip = true
                }
                .background(t.tile)
                .then(if (focused) Modifier.border(t.borderWidth, t.focusBorder, cardShape) else Modifier)
                .remoteClickable(onClick = onClick, onFocus = {
                    focused = it
                    if (it) onFocused()
                }),
        ) {
            image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            val progress = item.progress
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(t.accent))
                }
            }
        }
        TvText(item.title, 13.sp, color = if (focused) t.fg else t.fgMuted, modifier = Modifier.width(width))
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
