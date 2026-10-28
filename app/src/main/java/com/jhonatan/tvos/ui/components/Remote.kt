package com.jhonatan.tvos.ui.components

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText

private val SELECT_KEYS = setOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER,
)

/**
 * Elemento enfocable con el mando: OK = clic, mantener OK = pulsación larga.
 * También responde a ratón/táctil (útil con AnyDesk).
 */
fun Modifier.remoteClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
): Modifier = composed {
    // [0] = se vio ACTION_DOWN aquí, [1] = ya se disparó la pulsación larga
    val state = remember { BooleanArray(2) }
    this
        .onFocusChanged { onFocus(it.isFocused) }
        .focusable()
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.keyCode !in SELECT_KEYS) return@onPreviewKeyEvent false
            when (native.action) {
                NativeKeyEvent.ACTION_DOWN -> {
                    state[0] = true
                    if (native.repeatCount > 0 && onLongClick != null && !state[1]) {
                        state[1] = true
                        onLongClick()
                    }
                    true
                }
                NativeKeyEvent.ACTION_UP -> {
                    // Ignora el UP de una pulsación que empezó en otra pantalla.
                    if (state[0] && !state[1]) onClick()
                    state[0] = false
                    state[1] = false
                    true
                }
                else -> false
            }
        }
        .pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongClick?.invoke() },
            )
        }
}

/** Botón de texto: se invierte al recibir el foco, como en tvOS. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val t = LocalTv.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        focused -> t.tileFocused
        selected -> t.accent
        else -> t.tile
    }
    val fg = when {
        focused -> t.tileFocusedFg
        selected -> androidx.compose.ui.graphics.Color.White
        else -> t.fg
    }
    Box(
        modifier = modifier
            .background(bg, shape)
            .then(if (focused) Modifier.border(t.borderWidth, t.focusBorder, shape) else Modifier)
            .remoteClickable(onClick = onClick, onFocus = { focused = it })
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TvText(text, 16.sp, weight = FontWeight.SemiBold, color = fg)
    }
}
