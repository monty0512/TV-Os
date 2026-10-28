package com.jhonatan.tvos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.Profile
import com.jhonatan.tvos.ui.components.ProfileAvatar
import com.jhonatan.tvos.ui.components.displayName
import com.jhonatan.tvos.ui.components.greeting
import com.jhonatan.tvos.ui.theme.LocalReducedEffects
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText
import kotlinx.coroutines.delay

private const val WELCOME_MS = 1_800L

/** Bienvenida al elegir perfil: avatar, "¡Hola, [nombre]!" y el saludo según la hora; se desvanece sola. */
@Composable
fun WelcomeOverlay(profile: Profile?, onDone: () -> Unit) {
    val t = LocalTv.current
    // Se conserva el último perfil para poder mostrarlo mientras se desvanece.
    var shown by remember { mutableStateOf<Profile?>(null) }
    if (profile != null) shown = profile
    LaunchedEffect(profile) {
        if (profile != null) {
            delay(WELCOME_MS)
            onDone()
        }
    }
    val duration = if (LocalReducedEffects.current) 150 else 350
    AnimatedVisibility(
        visible = profile != null,
        enter = fadeIn(androidx.compose.animation.core.tween(duration)),
        exit = fadeOut(androidx.compose.animation.core.tween(duration)),
    ) {
        val p = shown ?: return@AnimatedVisibility
        val fallback = stringResource(R.string.profile_default)
        Column(
            Modifier.fillMaxSize().background(t.bg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            ProfileAvatar(p, 120.dp, fallback)
            TvText(stringResource(R.string.welcome_title, p.displayName(fallback)), 40.sp, weight = FontWeight.Bold)
            TvText(greeting(p.name), 20.sp, color = t.fgMuted)
        }
    }
}
