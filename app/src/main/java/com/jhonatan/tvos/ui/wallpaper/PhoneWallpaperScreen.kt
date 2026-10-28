package com.jhonatan.tvos.ui.wallpaper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jhonatan.tvos.data.PhotoKind
import com.jhonatan.tvos.system.Qr
import com.jhonatan.tvos.system.WallpaperServer
import com.jhonatan.tvos.ui.components.TvButton
import com.jhonatan.tvos.ui.home.SidePadding
import com.jhonatan.tvos.ui.theme.LocalTv
import com.jhonatan.tvos.ui.theme.TvText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

private enum class Status { WAITING, RECEIVED, NO_NETWORK }

/**
 * Pantalla 8 del diseño (fondo o foto de perfil): QR + dirección + código de 4 dígitos. El servidor solo vive mientras esta pantalla
 * está abierta. [onPhoto] guarda la foto (en segundo plano) y devuelve false si no es una imagen válida.
 */
@Composable
fun PhoneWallpaperScreen(
    kind: PhotoKind,
    profileName: String,
    hasPhoto: Boolean,
    onPhoto: (ByteArray) -> Boolean,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTv.current
    val scope = rememberCoroutineScope()
    val code = remember { Random.nextInt(1000, 10000).toString() }
    var status by remember { mutableStateOf(Status.WAITING) }
    var url by remember { mutableStateOf<String?>(null) }
    val isAvatar = kind == PhotoKind.AVATAR
    val pageHeading = stringResource(if (isAvatar) R.string.av_page_heading else R.string.wp_page_heading)
    val pageNote = stringResource(if (isAvatar) R.string.av_page_note else R.string.wp_page_note)

    DisposableEffect(Unit) {
        val server = WallpaperServer(code, pageHeading, pageNote) { bytes ->
            onPhoto(bytes).also { ok -> if (ok) status = Status.RECEIVED }
        }
        server.start(scope)
        val ip = WallpaperServer.localAddress()
        if (server.port == null || ip == null) status = Status.NO_NETWORK else url = "http://$ip:${server.port}"
        onDispose { server.stop() }
    }
    // Tras recibir la foto, vuelve solo a Apariencia.
    LaunchedEffect(status) {
        if (status == Status.RECEIVED) {
            delay(2_500)
            onBack()
        }
    }
    var qr by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) {
        qr = url?.let { withContext(Dispatchers.Default) { Qr.bitmap(it, 480) } }
    }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocus.requestFocus() }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(t.bg)
            .padding(horizontal = SidePadding * 1.5f, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TvText(
                stringResource(if (isAvatar) R.string.av_profile else R.string.wp_profile, profileName).uppercase(),
                13.sp, weight = FontWeight.SemiBold, color = t.fgMuted,
            )
            TvText(stringResource(if (isAvatar) R.string.av_title else R.string.wp_title), 34.sp, weight = FontWeight.Bold, maxLines = 2)
            Step(1, stringResource(R.string.wp_step_wifi))
            Step(2, stringResource(R.string.wp_step_scan))
            Step(3, stringResource(R.string.wp_step_send))
            url?.let { TvText(stringResource(R.string.wp_manual, it), 15.sp, color = t.fgMuted, maxLines = 2) }
            TvText(stringResource(R.string.wp_code, code), 22.sp, weight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(stringResource(R.string.back), onClick = onBack, modifier = Modifier.focusRequester(backFocus))
                if (hasPhoto) TvButton(stringResource(if (isAvatar) R.string.av_remove else R.string.wp_remove), onClick = onRemove)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(260.dp).background(Color.White, RoundedCornerShape(20.dp)).padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                qr?.let { Image(it, contentDescription = url, modifier = Modifier.fillMaxSize()) }
            }
            TvText(
                text = stringResource(
                    when (status) {
                        Status.WAITING -> R.string.wp_waiting
                        Status.RECEIVED -> R.string.wp_received
                        Status.NO_NETWORK -> R.string.wp_no_network
                    },
                ),
                size = 16.sp,
                weight = if (status == Status.RECEIVED) FontWeight.SemiBold else FontWeight.Normal,
                color = if (status == Status.RECEIVED) Color(0xFF30D158) else t.fgMuted,
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val t = LocalTv.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(t.tile, CircleShape), contentAlignment = Alignment.Center) {
            TvText("$n", 14.sp, weight = FontWeight.Bold)
        }
        TvText(text, 17.sp, maxLines = 2)
    }
}
