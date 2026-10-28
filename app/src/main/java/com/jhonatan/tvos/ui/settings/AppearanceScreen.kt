package com.jhonatan.tvos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.Density
import com.jhonatan.tvos.data.ThemeMode
import com.jhonatan.tvos.data.UserSettings
import com.jhonatan.tvos.ui.components.TvButton
import com.jhonatan.tvos.ui.components.remoteClickable
import com.jhonatan.tvos.ui.theme.Accents
import com.jhonatan.tvos.ui.theme.LocalReducedEffects
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText

/** Pantalla 6 del diseño: tema, acento, tamaño y accesibilidad. Los cambios se aplican al instante. */
@Composable
fun AppearanceScreen(
    settings: UserSettings,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onReorder: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTv.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val yes = stringResource(R.string.yes)
    val no = stringResource(R.string.no)

    Column(
        Modifier
            .fillMaxSize()
            .background(t.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TvText(stringResource(R.string.appearance), 30.sp, weight = FontWeight.Bold)

        Section(stringResource(R.string.theme)) {
            val options = listOf(
                ThemeMode.DARK to R.string.theme_dark,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.AUTO to R.string.theme_auto,
            )
            options.forEachIndexed { i, (mode, label) ->
                TvButton(
                    text = stringResource(label),
                    selected = settings.theme == mode,
                    onClick = { onChange { it.copy(theme = mode) } },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }

        Section(stringResource(R.string.accent)) {
            val names = listOf(R.string.accent_blue, R.string.accent_orange, R.string.accent_green, R.string.accent_purple)
            Accents.forEachIndexed { i, color ->
                val name = stringResource(names[i])
                var focused by remember { mutableStateOf(false) }
                val ring = when {
                    focused -> Modifier.border(4.dp, t.fg, CircleShape)
                    settings.accent == i -> Modifier.border(3.dp, t.fgMuted, CircleShape)
                    else -> Modifier
                }
                Box(
                    Modifier
                        .size(if (focused) 48.dp else 44.dp)
                        .then(ring)
                        .padding(5.dp)
                        .background(color, CircleShape)
                        .semantics { contentDescription = name }
                        .remoteClickable(onClick = { onChange { it.copy(accent = i) } }, onFocus = { focused = it }),
                )
            }
        }

        Section(stringResource(R.string.size)) {
            TvButton(
                text = stringResource(R.string.size_large),
                selected = settings.density == Density.LARGE,
                onClick = { onChange { it.copy(density = Density.LARGE) } },
            )
            TvButton(
                text = stringResource(R.string.size_medium),
                selected = settings.density == Density.MEDIUM,
                onClick = { onChange { it.copy(density = Density.MEDIUM) } },
            )
            TvButton(
                text = stringResource(R.string.size_compact),
                selected = settings.density == Density.COMPACT,
                onClick = { onChange { it.copy(density = Density.COMPACT) } },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = stringResource(R.string.toggle_value, stringResource(R.string.large_text), if (settings.largeText) yes else no),
                selected = settings.largeText,
                onClick = { onChange { it.copy(largeText = !it.largeText) } },
            )
            TvButton(
                text = stringResource(R.string.toggle_value, stringResource(R.string.high_contrast), if (settings.highContrast) yes else no),
                selected = settings.highContrast,
                onClick = { onChange { it.copy(highContrast = !it.highContrast) } },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(text = stringResource(R.string.reorder_apps), onClick = onReorder)
            TvButton(text = stringResource(R.string.back), onClick = onBack)
        }

        val context = LocalContext.current
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        }
        TvText(
            text = "TV-Os $version · " + stringResource(if (LocalReducedEffects.current) R.string.perf_reduced else R.string.perf_full),
            size = 13.sp,
            color = t.fgMuted,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvText(title, 14.sp, color = LocalTv.current.fgMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}
