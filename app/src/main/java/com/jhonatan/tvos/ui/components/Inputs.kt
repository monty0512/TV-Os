package com.jhonatan.tvos.ui.components

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.jhonatan.tvos.data.Backgrounds
import com.jhonatan.tvos.data.Images
import com.jhonatan.tvos.data.PhotoKind
import com.jhonatan.tvos.data.Profile
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.ProfileColors
import com.jhonatan.tvos.ui.theme.TvText

/** Nombre visible de un perfil (el migrado de la Fase 1 no tiene nombre). */
fun Profile.displayName(fallback: String) = name.ifBlank { fallback }

/** Círculo con la inicial del perfil. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, fallbackName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uri = Backgrounds.uri(context, PhotoKind.AVATAR, profile)
    var photo by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) { photo = uri?.let { Images.load(context, it, PhotoKind.AVATAR.width, PhotoKind.AVATAR.height) } }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ProfileColors.getOrElse(profile.color) { ProfileColors[0] }),
        contentAlignment = Alignment.Center,
    ) {
        val image = photo
        if (image != null) {
            Image(image, contentDescription = profile.displayName(fallbackName), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            TvText(
                text = profile.displayName(fallbackName).take(1).uppercase(),
                size = (size.value * 0.42f).sp,
                weight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/**
 * Campo de texto para el mando: OK abre el teclado en pantalla, "Hecho" lo cierra.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    width: Dp = 320.dp,
    onDone: () -> Unit = {},
) {
    val t = LocalTv.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(20)) },
        singleLine = true,
        textStyle = TextStyle(color = t.fg, fontSize = 18.sp * t.textScale),
        cursorBrush = SolidColor(t.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            onDone()
        }),
        modifier = modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val n = event.nativeKeyEvent
                if (n.action == NativeKeyEvent.ACTION_UP &&
                    (n.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || n.keyCode == NativeKeyEvent.KEYCODE_ENTER)
                ) {
                    keyboard?.show()
                    true
                } else {
                    false
                }
            }
            .background(t.tile, shape)
            .border(if (focused) t.borderWidth else 1.dp, if (focused) t.focusBorder else t.fgMuted.copy(alpha = 0.4f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) TvText(placeholder, 18.sp, color = t.fgMuted)
                inner()
            }
        },
    )
}

/** Selector de color de perfil (círculos). */
@Composable
fun ProfileColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    val t = LocalTv.current
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileColors.forEachIndexed { i, color ->
            var focused by remember { mutableStateOf(false) }
            val ring = when {
                focused -> Modifier.border(4.dp, t.fg, CircleShape)
                selected == i -> Modifier.border(3.dp, t.fgMuted, CircleShape)
                else -> Modifier
            }
            Box(
                Modifier
                    .size(44.dp)
                    .then(ring)
                    .padding(5.dp)
                    .background(color, CircleShape)
                    .remoteClickable(onClick = { onSelect(i) }, onFocus = { focused = it }),
            )
        }
    }
}
