package com.jhonatan.tvos.ui.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.ProfilesState
import com.jhonatan.tvos.ui.components.ProfileAvatar
import com.jhonatan.tvos.ui.components.ProfileColorPicker
import com.jhonatan.tvos.ui.components.TvButton
import com.jhonatan.tvos.ui.components.TvTextField
import com.jhonatan.tvos.ui.components.displayName
import com.jhonatan.tvos.ui.components.remoteClickable
import com.jhonatan.tvos.ui.theme.LocalReducedEffects
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText

private const val MAX_PROFILES = 6

/** Pantalla 3 del diseño: "¿Quién está viendo?". Mantener OK sobre un perfil permite eliminarlo. */
@Composable
fun ProfilesScreen(
    state: ProfilesState,
    onPick: (id: String) -> Unit,
    onAdd: (name: String, color: Int) -> Unit,
    onRemove: (id: String) -> Unit,
) {
    val t = LocalTv.current
    val fallback = stringResource(R.string.profile_default)
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val activeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { activeFocus.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().background(t.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterVertically),
    ) {
        TvText(stringResource(R.string.who_watching), 32.sp, weight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
            state.profiles.forEach { p ->
                ProfileChoice(
                    label = p.displayName(fallback),
                    modifier = if (p.id == state.active?.id) Modifier.focusRequester(activeFocus) else Modifier,
                    onClick = { onPick(p.id) },
                    onLongClick = { if (state.profiles.size > 1) pendingDelete = p.id },
                ) { ProfileAvatar(p, 112.dp, fallback) }
            }
            if (state.profiles.size < MAX_PROFILES) {
                ProfileChoice(label = stringResource(R.string.add_profile), onClick = { adding = true }) {
                    Box(
                        Modifier.size(112.dp).border(2.dp, t.fgMuted, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { TvText("+", 44.sp, color = t.fgMuted) }
                }
            }
        }

        val deleting = state.profiles.firstOrNull { it.id == pendingDelete }
        when {
            deleting != null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvText(stringResource(R.string.delete_profile_q, deleting.displayName(fallback)), 18.sp)
                TvButton(stringResource(R.string.delete), onClick = {
                    onRemove(deleting.id)
                    pendingDelete = null
                })
                TvButton(stringResource(R.string.cancel), onClick = { pendingDelete = null })
            }
            adding -> NewProfileForm(
                onSave = { name, color ->
                    onAdd(name, color)
                    adding = false
                },
                onCancel = { adding = false },
            )
            else -> TvText(stringResource(R.string.hold_to_delete), 14.sp, color = t.fgMuted)
        }
    }
}

@Composable
private fun ProfileChoice(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    avatar: @Composable () -> Unit,
) {
    val t = LocalTv.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.12f else 1f,
        tween(if (LocalReducedEffects.current) 90 else 160),
        label = "avatar",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (focused) Modifier.border(t.borderWidth + 1.dp, t.fg, CircleShape) else Modifier)
                .remoteClickable(onClick = onClick, onLongClick = onLongClick, onFocus = { focused = it }),
        ) { avatar() }
        TvText(label, 18.sp, weight = if (focused) FontWeight.SemiBold else FontWeight.Normal, color = if (focused) t.fg else t.fgMuted)
    }
}

/** Nombre + color; se usa aquí y en la bienvenida. */
@Composable
fun NewProfileForm(
    onSave: (name: String, color: Int) -> Unit,
    onCancel: (() -> Unit)?,
    initialColor: Int = 0,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(initialColor) }
    val save = {
        if (name.isNotBlank()) {
            onSave(name, color)
            name = ""
            color = (color + 1) % 6
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(name, { name = it }, stringResource(R.string.profile_name_hint), onDone = save)
            TvButton(stringResource(R.string.save), onClick = save)
            if (onCancel != null) TvButton(stringResource(R.string.cancel), onClick = onCancel)
        }
        ProfileColorPicker(color) { color = it }
    }
}
