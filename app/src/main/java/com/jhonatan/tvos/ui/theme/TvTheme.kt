package com.jhonatan.tvos.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jhonatan.tvos.data.ThemeMode
import com.jhonatan.tvos.data.UserSettings

val Accents = listOf(
    Color(0xFF0A84FF),
    Color(0xFFFF9F0A),
    Color(0xFF30D158),
    Color(0xFFBF5AF2),
)

/** Colores de avatar de los perfiles. */
val ProfileColors = listOf(
    Color(0xFF0A84FF),
    Color(0xFFBF5AF2),
    Color(0xFFFF9F0A),
    Color(0xFF30D158),
    Color(0xFFFF375F),
    Color(0xFF64D2FF),
)

data class TvColors(
    val isDark: Boolean,
    val bg: Color,
    val fg: Color,
    val fgMuted: Color,
    val tile: Color,
    val tileFocused: Color,
    val tileFocusedFg: Color,
    val accent: Color,
    val focusBorder: Color,
    val borderWidth: Dp,
    val textScale: Float,
)

fun tvColors(s: UserSettings, hour: Int): TvColors {
    val dark = when (s.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> hour !in 7..18
    }
    val accent = Accents.getOrElse(s.accent) { Accents[0] }
    val fg = if (dark) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)
    return TvColors(
        isDark = dark,
        bg = if (dark) Color(0xFF0B0B0D) else Color(0xFFF2F2F7),
        fg = fg,
        fgMuted = if (s.highContrast) fg else if (dark) Color(0xFFC7C7CC) else Color(0xFF48484A),
        tile = if (dark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF),
        tileFocused = fg,
        tileFocusedFg = if (dark) Color(0xFF0B0B0D) else Color(0xFFFFFFFF),
        accent = accent,
        focusBorder = if (s.highContrast) fg else accent,
        borderWidth = if (s.highContrast) 5.dp else 3.dp,
        textScale = if (s.largeText) 1.25f else 1f,
    )
}

val LocalTv = staticCompositionLocalOf { tvColors(UserSettings(), 12) }

/** true en equipos con poca memoria: sin sombras ni transiciones de color. */
val LocalReducedEffects = staticCompositionLocalOf { true }

@Composable
fun TvText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalTv.current.fg,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size * LocalTv.current.textScale, fontWeight = weight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
