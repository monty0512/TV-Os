package com.jhonatan.tvos.system

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Administrador de dispositivo: solo pide "bloquear pantalla" para apagar la TV (temporizador y botón Apagar). */
class TvOsAdminReceiver : DeviceAdminReceiver()

/** Salta cuando vence el temporizador de apagado. */
class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SleepTimer.clear(context)
        Power.turnOff(context)
    }
}

object Power {
    fun isAvailable(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isAdminActive(ComponentName(context, TvOsAdminReceiver::class.java))
    }

    /** Quita el permiso de apagado (necesario antes de desinstalar TV-Os). */
    fun disable(context: Context) {
        runCatching {
            context.getSystemService(DevicePolicyManager::class.java)
                ?.removeActiveAdmin(ComponentName(context, TvOsAdminReceiver::class.java))
        }
    }

    /** Apaga la pantalla (reposo). Requiere que el script de instalación active el administrador. */
    fun turnOff(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return runCatching { context.getSystemService(DevicePolicyManager::class.java)?.lockNow() }.isSuccess
    }
}

object SleepTimer {
    val OPTIONS_MIN = listOf(0, 15, 30, 60, 90)
    private const val PREFS = "sleep_timer"
    private const val KEY_AT = "at_elapsed"

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, SleepReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 0 = desactivar. */
    fun set(context: Context, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context))
        if (minutes <= 0) {
            clear(context)
            return
        }
        val at = SystemClock.elapsedRealtime() + minutes * 60_000L
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_AT, at).apply()
    }

    /** Minutos que faltan, o 0 si no hay temporizador. */
    fun remainingMinutes(context: Context): Int {
        val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0L)
        val left = at - SystemClock.elapsedRealtime()
        return if (at == 0L || left <= 0) 0 else ((left + 59_999) / 60_000).toInt()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_AT).apply()
    }
}

object Memory {
    /**
     * Cierra las apps instaladas por el usuario que están en segundo plano.
     * Android no deja cerrar la app en primer plano ni servicios del sistema (se dejan en paz a propósito).
     * Devuelve los MB que el sistema reporta como recuperados.
     */
    suspend fun freeBackgroundApps(context: Context): Int = withContext(Dispatchers.IO) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return@withContext 0
        val before = availableMb(am)
        context.packageManager.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
            .forEach { runCatching { am.killBackgroundProcesses(it.packageName) } }
        delay(1_000)
        (availableMb(am) - before).coerceAtLeast(0)
    }

    fun availableMb(am: ActivityManager): Int {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.availMem / (1024 * 1024)).toInt()
    }
}

data class TvInput(val id: String, val label: String)

object Inputs {
    /** Entradas físicas (HDMI, AV…) que expone el sistema de TV. */
    fun list(context: Context): List<TvInput> {
        val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching {
            tim.tvInputList
                .filter { it.isPassthroughInput }
                .map { TvInput(it.id, it.loadLabel(context).toString()) }
        }.getOrDefault(emptyList())
    }

    /** Cambia de entrada a través de la app de TV del fabricante. Se verifica en el MiTV. */
    fun open(context: Context, input: TvInput): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}

object Volume {
    private fun am(context: Context) = context.getSystemService(AudioManager::class.java)

    /** Volumen actual en 0..1. */
    fun level(context: Context): Float {
        val am = am(context) ?: return 0f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun step(context: Context, up: Boolean) {
        am(context)?.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
    }
}

enum class NetworkKind { WIFI, ETHERNET, NONE }

object Network {
    fun kind(context: Context): NetworkKind {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.NONE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkKind.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            else -> NetworkKind.NONE
        }
    }
}

object SystemSettings {
    fun openWifi(context: Context) = open(context, Settings.ACTION_WIFI_SETTINGS)

    /** Ajustes de imagen: Android TV no tiene un atajo estándar; se intenta pantalla y, si no, Ajustes. */
    fun openPicture(context: Context) =
        open(context, Settings.ACTION_DISPLAY_SETTINGS) || open(context, Settings.ACTION_SETTINGS)

    private fun open(context: Context, action: String): Boolean = runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
