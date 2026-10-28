package com.jhonatan.tvos.ui.edit

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.Density
import com.jhonatan.tvos.data.TvApp
import com.jhonatan.tvos.ui.components.AppTile
import com.jhonatan.tvos.ui.components.TvButton
import com.jhonatan.tvos.ui.home.SidePadding
import com.jhonatan.tvos.ui.home.tileSize
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText

/**
 * Pantalla 5 del diseño, en cuadrícula: OK agarra/suelta la app y las flechas la mueven
 * en las 4 direcciones. La primera fila son las favoritas del Top Shelf.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    visible: List<TvApp>,
    hidden: List<TvApp>,
    density: Density,
    initialSelected: String?,
    onMove: (packageName: String, delta: Int) -> Unit,
    onHide: (packageName: String) -> Unit,
    onRestore: (packageName: String) -> Unit,
    onDone: () -> Unit,
) {
    val t = LocalTv.current
    val size = tileSize(density, LocalConfiguration.current.screenWidthDp.dp)
    val firstPkg = initialSelected ?: visible.firstOrNull()?.packageName
    var selected by remember { mutableStateOf(firstPkg) }
    var grabbed by remember { mutableStateOf(initialSelected != null) }
    val selectedLabel = visible.firstOrNull { it.packageName == selected }?.label ?: "—"

    val gridState = rememberLazyGridState()
    val initialFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val idx = visible.indexOfFirst { it.packageName == firstPkg }
        if (idx > 0) gridState.scrollToItem(idx)
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }.onFailure { runCatching { doneFocus.requestFocus() } }
    }
    // Al mover la app agarrada fuera de la vista, desplaza la cuadrícula para seguirla.
    LaunchedEffect(visible, selected, grabbed) {
        if (!grabbed) return@LaunchedEffect
        val idx = visible.indexOfFirst { it.packageName == selected }
        if (idx >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
            gridState.animateScrollToItem(idx)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.bg)
            .padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(horizontal = SidePadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvText(stringResource(R.string.edit_title), 28.sp, weight = FontWeight.Bold)
            TvText(stringResource(R.string.edit_hint), 14.sp, color = t.fgMuted, maxLines = 2)
        }

        Row(
            Modifier.padding(horizontal = SidePadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvText(
                text = stringResource(if (grabbed) R.string.edit_moving else R.string.edit_selected, selectedLabel),
                size = 16.sp,
                color = t.fgMuted,
                modifier = Modifier.padding(end = 12.dp),
            )
            TvButton(
                text = stringResource(R.string.hide),
                onClick = {
                    val pkg = selected ?: return@TvButton
                    val idx = visible.indexOfFirst { it.packageName == pkg }
                    onHide(pkg)
                    grabbed = false
                    selected = visible.getOrNull(idx + 1)?.packageName ?: visible.getOrNull(idx - 1)?.packageName
                },
            )
            TvButton(
                text = stringResource(R.string.done),
                onClick = onDone,
                modifier = Modifier.focusRequester(doneFocus),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(size.columns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = SidePadding, end = SidePadding, top = 14.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(size.gap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visible, key = { it.packageName }) { app ->
                val isSelected = app.packageName == selected
                AppTile(
                    app = app,
                    width = size.width,
                    height = size.height,
                    alwaysShowLabel = true,
                    grabbed = isSelected && grabbed,
                    onFocused = { if (!grabbed) selected = app.packageName },
                    onClick = {
                        if (isSelected) {
                            grabbed = !grabbed
                        } else {
                            selected = app.packageName
                            grabbed = true
                        }
                    },
                    onKey = { event ->
                        val native = event.nativeKeyEvent
                        val delta = when (native.keyCode) {
                            NativeKeyEvent.KEYCODE_DPAD_LEFT -> -1
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> 1
                            NativeKeyEvent.KEYCODE_DPAD_UP -> -size.columns
                            NativeKeyEvent.KEYCODE_DPAD_DOWN -> size.columns
                            else -> 0
                        }
                        if (delta != 0 && grabbed && isSelected) {
                            if (native.action == NativeKeyEvent.ACTION_DOWN) onMove(app.packageName, delta)
                            true
                        } else {
                            false
                        }
                    },
                    modifier = if (app.packageName == firstPkg) Modifier.focusRequester(initialFocus) else Modifier,
                )
            }

            item(key = "hidden", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .background(t.tile.copy(alpha = if (t.isDark) 0.6f else 1f), RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TvText(stringResource(R.string.hidden_title), 15.sp, weight = FontWeight.SemiBold)
                    if (hidden.isEmpty()) {
                        TvText(stringResource(R.string.none_hidden), 15.sp, color = t.fgMuted)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            hidden.forEach { app ->
                                TvButton(text = app.label, onClick = { onRestore(app.packageName) })
                            }
                        }
                    }
                }
            }
        }
    }
}
