package com.jhonatan.tvos.ui.edit

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.TvApp
import com.jhonatan.tvos.ui.components.AppTile
import com.jhonatan.tvos.ui.components.TvButton
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText

/** Pantalla 5 del diseño: ordenar y ocultar apps con el mando. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    visible: List<TvApp>,
    hidden: List<TvApp>,
    initialSelected: String?,
    onMove: (packageName: String, delta: Int) -> Unit,
    onHide: (packageName: String) -> Unit,
    onRestore: (packageName: String) -> Unit,
    onDone: () -> Unit,
) {
    val t = LocalTv.current
    var selected by remember { mutableStateOf(initialSelected ?: visible.firstOrNull()?.packageName) }
    var grabbed by remember { mutableStateOf(initialSelected != null) }
    val selectedLabel = visible.firstOrNull { it.packageName == selected }?.label ?: "—"

    val initialFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }.onFailure { runCatching { doneFocus.requestFocus() } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.bg)
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvText(stringResource(R.string.edit_title), 30.sp, weight = FontWeight.Bold)
            TvText(stringResource(R.string.edit_hint), 15.sp, color = t.fgMuted, maxLines = 2)
        }

        LazyRow(
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(visible, key = { it.packageName }) { app ->
                val isSelected = app.packageName == selected
                AppTile(
                    app = app,
                    width = 128.dp,
                    height = 72.dp,
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
                            else -> 0
                        }
                        if (delta != 0 && grabbed && isSelected) {
                            if (native.action == NativeKeyEvent.ACTION_DOWN) onMove(app.packageName, delta)
                            true
                        } else {
                            false
                        }
                    },
                    modifier = if (app.packageName == (initialSelected ?: visible.firstOrNull()?.packageName)) {
                        Modifier.focusRequester(initialFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
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

        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
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
