package com.jhonatan.tvos.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.data.TvApp
import com.jhonatan.tvos.ui.theme.LocalReducedEffects
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText

private val TileShape = RoundedCornerShape(14.dp)

/**
 * Tile de app al estilo tvOS: al enfocarse crece, se eleva y muestra su nombre.
 * [grabbed] = modo edición, la app se está moviendo (se inclina y queda marcada).
 */
@Composable
fun AppTile(
    app: TvApp,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    grabbed: Boolean = false,
    alwaysShowLabel: Boolean = false,
    onKey: ((KeyEvent) -> Boolean)? = null,
) {
    val t = LocalTv.current
    val reduced = LocalReducedEffects.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = tween(if (reduced) 90 else 160),
        label = "tileScale",
    )
    val elevation = if (focused && !reduced) 14.dp else 0.dp

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val tileModifier = Modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = if (grabbed) -2f else 0f
                shadowElevation = elevation.toPx()
                shape = TileShape
                clip = true
            }
            .then(
                if (focused || grabbed) Modifier.border(t.borderWidth, t.focusBorder, TileShape) else Modifier,
            )
            .then(if (onKey != null) Modifier.onPreviewKeyEvent(onKey) else Modifier)
            .remoteClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onFocus = {
                    focused = it
                    if (it) onFocused()
                },
            )
        // Un único nodo enfocable: el foco no se pierde cuando el banner se libera o se recarga.
        // Sin banner (volviendo de otra app) se ve el color de la app mientras se recarga.
        Box(tileModifier.background(app.color)) {
            val banner = app.banner
            if (banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        TvText(
            text = app.label,
            size = 13.sp,
            weight = FontWeight.Medium,
            color = if (focused || alwaysShowLabel) t.fg else Color.Transparent,
            modifier = Modifier.width(width),
        )
    }
}
