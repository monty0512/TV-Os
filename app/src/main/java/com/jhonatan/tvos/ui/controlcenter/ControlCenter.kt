package com.jhonatan.tvos.ui.controlcenter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhonatan.tvos.R
import com.jhonatan.tvos.data.Profile
import com.jhonatan.tvos.system.Inputs
import com.jhonatan.tvos.system.Memory
import com.jhonatan.tvos.system.Network
import com.jhonatan.tvos.system.NetworkKind
import com.jhonatan.tvos.system.Power
import com.jhonatan.tvos.system.SleepTimer
import com.jhonatan.tvos.system.SystemSettings
import com.jhonatan.tvos.system.Volume
import com.jhonatan.tvos.ui.components.ProfileAvatar
import com.jhonatan.tvos.ui.components.TvButton
import com.jhonatan.tvos.ui.components.displayName
import com.jhonatan.tvos.ui.components.remoteClickable
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla 2 del diseño: panel lateral sobre la pantalla actual.
 * Se abre con el botón ≡ del mando (o la pulsación larga de Inicio, si la TV la envía a TV-Os).
 * El foco queda atrapado dentro del panel; Atrás o ≡ lo cierran.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControlCenter(
    profile: Profile?,
    onSwitchProfile: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalTv.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fallback = stringResource(R.string.profile_default)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    var freeing by remember { mutableStateOf(false) }
    var freedMb by remember { mutableStateOf<Int?>(null) }
    var sleepMin by remember { mutableIntStateOf(SleepTimer.remainingMinutes(context)) }
    var showInputs by remember { mutableStateOf(false) }
    val inputs = remember { Inputs.list(context) }
    var volume by remember { mutableStateOf(Volume.level(context)) }
    var freeMb by remember { mutableIntStateOf(Memory.availableMb(context)) }
    val totalMb = remember { Memory.totalMb(context) }
    var powerMissing by remember { mutableStateOf(false) }
    val network = remember { Network.kind(context) }
    val now = remember { Date() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(24.dp)
                .width(380.dp)
                .fillMaxHeight()
                .background(if (t.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7), RoundedCornerShape(24.dp))
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                TvText(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), 26.sp, weight = FontWeight.SemiBold)
                Box(Modifier.weight(1f))
                TvText(DateFormat.getDateInstance(DateFormat.MEDIUM).format(now), 13.sp, color = t.fgMuted)
            }

            // Perfil
            Row(
                Modifier.fillMaxWidth().background(t.tile, RoundedCornerShape(16.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (profile != null) ProfileAvatar(profile, 40.dp, fallback)
                TvText(profile?.displayName(fallback) ?: fallback, 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TvButton(stringResource(R.string.cc_switch_profile), onClick = onSwitchProfile, modifier = Modifier.focusRequester(first))
            }

            // Liberar memoria · Temporizador
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile(
                    title = stringResource(R.string.cc_free_memory),
                    value = when {
                        freeing -> stringResource(R.string.cc_freeing)
                        freedMb != null -> stringResource(R.string.cc_free_of_freed, freeMb, totalMb, freedMb ?: 0)
                        else -> stringResource(R.string.cc_free_of, freeMb, totalMb)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!freeing) {
                            freeing = true
                            scope.launch {
                                freedMb = Memory.freeBackgroundApps(context)
                                freeMb = Memory.availableMb(context)
                                freeing = false
                            }
                        }
                    },
                )
                CcTile(
                    title = stringResource(R.string.cc_sleep),
                    value = if (sleepMin > 0) stringResource(R.string.cc_sleep_min, sleepMin) else stringResource(R.string.cc_sleep_off),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // Cicla: no → 15 → 30 → 60 → 90 → no
                        val options = SleepTimer.OPTIONS_MIN
                        val next = options.firstOrNull { it > sleepMin } ?: 0
                        if (next > 0 && !Power.isAvailable(context)) {
                            powerMissing = true
                        } else {
                            SleepTimer.set(context, next)
                            sleepMin = next
                        }
                    },
                )
            }

            // Entradas · Wi-Fi · Imagen
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile(
                    title = stringResource(R.string.cc_inputs),
                    value = if (inputs.isEmpty()) stringResource(R.string.cc_no_inputs) else "${inputs.size}",
                    modifier = Modifier.weight(1f),
                    onClick = { showInputs = !showInputs },
                )
                CcTile(
                    title = stringResource(
                        when (network) {
                            NetworkKind.WIFI -> R.string.cc_wifi
                            NetworkKind.ETHERNET -> R.string.cc_ethernet
                            NetworkKind.NONE -> R.string.cc_no_network
                        },
                    ),
                    value = "",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        SystemSettings.openWifi(context)
                        onClose()
                    },
                )
                CcTile(
                    title = stringResource(R.string.cc_picture),
                    value = "",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        SystemSettings.openPicture(context)
                        onClose()
                    },
                )
            }
            CcTile(
                title = stringResource(R.string.cc_settings),
                value = "",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    SystemSettings.openSettings(context)
                    onClose()
                },
            )
            if (showInputs) {
                inputs.forEach { input ->
                    TvButton(input.label, onClick = {
                        Inputs.open(context, input)
                        onClose()
                    }, modifier = Modifier.fillMaxWidth())
                }
            }

            // Volumen
            Column(
                Modifier.fillMaxWidth().background(t.tile, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val (vol, volMax) = volume
                Row(Modifier.fillMaxWidth()) {
                    TvText(stringResource(R.string.cc_volume), 14.sp, color = t.fgMuted, modifier = Modifier.weight(1f))
                    TvText("$vol / $volMax", 14.sp, weight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton("−", onClick = {
                        Volume.step(context, up = false)
                        volume = Volume.level(context)
                    })
                    Box(Modifier.weight(1f).height(8.dp).background(t.fgMuted.copy(alpha = 0.3f), RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(vol.toFloat() / volMax).height(8.dp).background(t.fg, RoundedCornerShape(4.dp)))
                    }
                    TvButton("+", onClick = {
                        Volume.step(context, up = true)
                        volume = Volume.level(context)
                    })
                }
            }

            if (powerMissing) {
                TvText(stringResource(R.string.cc_power_unavailable), 13.sp, color = Color(0xFFFF9F0A), maxLines = 2)
            }
            TvButton(stringResource(R.string.cc_power_off), modifier = Modifier.fillMaxWidth(), onClick = {
                if (!Power.turnOff(context)) powerMissing = true
            })
            TvText(stringResource(R.string.cc_hint), 12.sp, color = t.fgMuted)
        }
    }
}

@Composable
private fun CcTile(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTv.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .height(84.dp)
            .background(if (focused) t.tileFocused else t.tile, shape)
            .then(if (focused) Modifier.border(t.borderWidth, t.focusBorder, shape) else Modifier)
            .remoteClickable(onClick = onClick, onFocus = { focused = it })
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val fg = if (focused) t.tileFocusedFg else t.fg
        TvText(title, 14.sp, weight = FontWeight.SemiBold, color = fg, maxLines = 2)
        if (value.isNotEmpty()) TvText(value, 13.sp, color = fg)
    }
}
