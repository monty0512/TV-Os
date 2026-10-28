package com.jhonatan.tvos.ui.home

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

data class TileSize(val width: Dp, val height: Dp, val gap: Dp, val shelf: Dp)

fun tileSize(density: Density) = when (density) {
    Density.LARGE -> TileSize(156.dp, 88.dp, 20.dp, 270.dp)
    Density.COMPACT -> TileSize(112.dp, 63.dp, 16.dp, 300.dp)
}

@Composable
fun HomeScreen(
    apps: List<TvApp>,
    density: Density,
    loading: Boolean,
    homeEvents: Int,
    onLaunch: (TvApp) -> Unit,
    onEdit: (TvApp) -> Unit,
    onPersonalize: () -> Unit,
) {
    val t = LocalTv.current
    val reduced = LocalReducedEffects.current
    val size = tileSize(density)
    var focusedIdx by remember { mutableIntStateOf(0) }
    val focusedApp = apps.getOrNull(focusedIdx)

    val shelfTarget = focusedApp?.let { if (t.isDark) darken(it.color, 0.6f) else lighten(it.color, 0.7f) } ?: t.tile
    val shelfColor by animateColorAsState(shelfTarget, tween(if (reduced) 0 else 300), label = "shelf")

    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Al pulsar Inicio (o al cargar): foco en la primera app.
    LaunchedEffect(homeEvents, apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(0)
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        // --- Top Shelf ---
        Box(
            Modifier
                .fillMaxWidth()
                .height(size.shelf)
                .background(shelfColor)
                .padding(horizontal = 48.dp, vertical = 28.dp),
        ) {
            TvText(
                text = stringResource(R.string.app_name).uppercase(),
                size = 12.sp,
                weight = FontWeight.SemiBold,
                color = t.fgMuted,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Clock(Modifier.align(Alignment.TopEnd))
            Column(Modifier.align(Alignment.BottomStart)) {
                TvText(stringResource(R.string.shelf_no_channel), 13.sp, color = t.fgMuted)
                Spacer(Modifier.height(6.dp))
                TvText(
                    text = focusedApp?.label ?: stringResource(R.string.loading_apps),
                    size = 52.sp,
                    weight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                TvText(stringResource(R.string.shelf_hint), 13.sp, color = t.fgMuted)
            }
        }

        // --- Fila de apps ---
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(size.gap),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
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
            item(key = "personalize") {
                PersonalizeTile(size, onPersonalize)
            }
        }
        if (loading && apps.isEmpty()) {
            TvText(stringResource(R.string.loading_apps), 16.sp, color = t.fgMuted, modifier = Modifier.padding(start = 48.dp))
        }
    }
}

@Composable
private fun PersonalizeTile(size: TileSize, onClick: () -> Unit) {
    val t = LocalTv.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(size.width, size.height)
            .background(if (focused) t.tileFocused else t.tile, shape)
            .then(if (focused) Modifier.border(t.borderWidth, t.focusBorder, shape) else Modifier)
            .remoteClickable(onClick = onClick, onFocus = { focused = it }),
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

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    TvText(format.format(Date(now)), 22.sp, modifier = modifier, weight = FontWeight.Medium)
}
