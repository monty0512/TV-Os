package com.jhonatan.tvos.ui.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.Density
import com.jhonatan.tvos.data.ProfilesState
import com.jhonatan.tvos.data.ThemeMode
import com.jhonatan.tvos.data.TvApp
import com.jhonatan.tvos.ui.components.AppTile
import com.jhonatan.tvos.ui.components.ProfileAvatar
import com.jhonatan.tvos.ui.components.TvButton
import com.jhonatan.tvos.ui.components.displayName
import com.jhonatan.tvos.ui.home.SidePadding
import com.jhonatan.tvos.ui.profiles.NewProfileForm
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText

/**
 * Pantalla 7 del diseño: bienvenida en 3 pasos (perfiles → apps favoritas → tema y tamaño).
 * Las favoritas, el tema y el tamaño se aplican a todos los perfiles creados; cada uno puede cambiarlos después.
 */
@Composable
fun OnboardingScreen(
    state: ProfilesState,
    apps: List<TvApp>,
    onAddProfile: (name: String, color: Int) -> Unit,
    onRenameUnnamed: (id: String, name: String, color: Int) -> Unit,
    onFinish: (favorites: List<String>, theme: ThemeMode, density: Density) -> Unit,
) {
    val t = LocalTv.current
    var step by remember { mutableIntStateOf(0) }
    val favorites = remember { mutableStateListOf<String>() }
    var theme by remember { mutableStateOf(state.active?.settings?.theme ?: ThemeMode.DARK) }
    var density by remember { mutableStateOf(state.active?.settings?.density ?: Density.COMPACT) }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.bg)
            .padding(top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        StepIndicator(step)
        when (step) {
            0 -> ProfilesStep(state, onAddProfile, onRenameUnnamed, onNext = { step = 1 })
            1 -> AppsStep(apps, favorites, onNext = { step = 2 })
            else -> ThemeStep(
                theme = theme,
                density = density,
                onTheme = { theme = it },
                onDensity = { density = it },
                onStart = { onFinish(favorites.toList(), theme, density) },
            )
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val t = LocalTv.current
    val labels = listOf(R.string.onb_step_profiles, R.string.onb_step_apps, R.string.onb_step_theme)
    Row(
        Modifier.padding(horizontal = SidePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val done = i < step
            val current = i == step
            Box(
                Modifier
                    .size(28.dp)
                    .background(
                        when {
                            done -> Color(0xFF30D158)
                            current -> t.fg
                            else -> t.tile
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TvText(if (done) "✓" else "${i + 1}", 14.sp, weight = FontWeight.Bold, color = if (current) t.bg else Color.White)
            }
            TvText(stringResource(label), 15.sp, weight = if (current) FontWeight.SemiBold else FontWeight.Normal, color = if (current) t.fg else t.fgMuted)
            if (i < labels.lastIndex) Box(Modifier.size(40.dp, 2.dp).background(t.tile))
        }
    }
}

@Composable
private fun Title(title: String, hint: String) {
    Column(Modifier.padding(horizontal = SidePadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvText(title, 34.sp, weight = FontWeight.Bold)
        TvText(hint, 16.sp, color = LocalTv.current.fgMuted, maxLines = 2)
    }
}

@Composable
private fun ProfilesStep(
    state: ProfilesState,
    onAdd: (String, Int) -> Unit,
    onRenameUnnamed: (String, String, Int) -> Unit,
    onNext: () -> Unit,
) {
    val fallback = stringResource(R.string.profile_default)
    val named = state.profiles.filter { it.name.isNotBlank() }
    val unnamed = state.profiles.firstOrNull { it.name.isBlank() }
    val nextFocus = remember { FocusRequester() }

    Title(stringResource(R.string.onb_profiles_title), stringResource(R.string.onb_profiles_hint))
    Row(
        Modifier.padding(horizontal = SidePadding),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        named.forEach { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileAvatar(p, 72.dp, fallback)
                TvText(p.displayName(fallback), 15.sp)
            }
        }
    }
    Box(Modifier.padding(horizontal = SidePadding)) {
        NewProfileForm(
            initialColor = named.size % 6,
            onSave = { name, color ->
                // El perfil migrado de la Fase 1 conserva sus ajustes: solo se le pone nombre.
                if (unnamed != null) onRenameUnnamed(unnamed.id, name, color) else onAdd(name, color)
            },
            onCancel = null,
        )
    }
    if (named.isNotEmpty()) {
        Row(Modifier.padding(horizontal = SidePadding)) {
            TvButton(stringResource(R.string.next), onClick = onNext, modifier = Modifier.focusRequester(nextFocus))
        }
    }
}

@Composable
private fun AppsStep(apps: List<TvApp>, favorites: MutableList<String>, onNext: () -> Unit) {
    val t = LocalTv.current
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Title(stringResource(R.string.onb_apps_title), stringResource(R.string.onb_apps_hint))
    Row(
        Modifier.padding(horizontal = SidePadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvText(stringResource(R.string.onb_apps_selected, favorites.size), 15.sp, color = t.fgMuted)
        TvButton(stringResource(R.string.next), onClick = onNext)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = SidePadding, end = SidePadding, top = 12.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            val position = favorites.indexOf(app.packageName)
            Box {
                AppTile(
                    app = app,
                    width = 126.dp,
                    height = 71.dp,
                    alwaysShowLabel = true,
                    grabbed = false,
                    onClick = {
                        if (position >= 0) favorites.remove(app.packageName) else favorites.add(app.packageName)
                    },
                    modifier = if (app == apps.first()) Modifier.focusRequester(first) else Modifier,
                )
                if (position >= 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(26.dp)
                            .background(t.accent, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { TvText("${position + 1}", 13.sp, weight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun ThemeStep(
    theme: ThemeMode,
    density: Density,
    onTheme: (ThemeMode) -> Unit,
    onDensity: (Density) -> Unit,
    onStart: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Title(stringResource(R.string.onb_theme_title), stringResource(R.string.onb_theme_hint))
    Column(Modifier.padding(horizontal = SidePadding), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TvText(stringResource(R.string.theme), 14.sp, color = LocalTv.current.fgMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                ThemeMode.DARK to R.string.theme_dark,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.AUTO to R.string.theme_auto,
            ).forEachIndexed { i, (mode, label) ->
                TvButton(
                    stringResource(label),
                    onClick = { onTheme(mode) },
                    selected = theme == mode,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        TvText(stringResource(R.string.size), 14.sp, color = LocalTv.current.fgMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Density.LARGE to R.string.size_large,
                Density.MEDIUM to R.string.size_medium,
                Density.COMPACT to R.string.size_compact,
            ).forEach { (d, label) ->
                TvButton(stringResource(label), onClick = { onDensity(d) }, selected = density == d)
            }
        }
        Spacer(Modifier.size(8.dp))
        Row { TvButton(stringResource(R.string.start), onClick = onStart, selected = true) }
    }
}
