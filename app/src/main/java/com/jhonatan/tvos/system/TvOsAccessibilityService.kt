package com.jhonatan.tvos.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.jhonatan.tvos.MainActivity

/**
 * Servicio de accesibilidad mínimo:
 * - Mantener ≡ o pulsar SET → Centro de control, desde cualquier app (ver [onKeyEvent]).
 * - Apagar pantalla (botón Apagar y temporizador), porque Xiaomi bloquea el administrador de dispositivo.
 * No lee el contenido de la pantalla.
 */
class TvOsAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "accesibilidad conectada")
    }

    override fun onDestroy() {
        handler.removeCallbacks(homeLongPress)
        if (instance === this) instance = null
        super.onDestroy()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var homeLongFired = false
    private val homeLongPress = Runnable {
        homeLongFired = true
        openControlCenter()
    }

    /**
     * Mando de Xiaomi (verificado con getevent):
     * - ≡ envía KEY_HOMEPAGE → KEYCODE_HOME: pulsación corta = Inicio; mantener = Centro de control.
     * - SET envía KEY_TASKMANAGER → KEYCODE_SETTINGS: Centro de control (los Ajustes están dentro).
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "a11y key ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}) scan=${event.scanCode}")
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_HOME -> handleHome(event)
            KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP) openControlCenter()
                true
            }
            else -> false
        }
    }

    private fun handleHome(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                homeLongFired = false
                handler.removeCallbacks(homeLongPress)
                handler.postDelayed(homeLongPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(homeLongPress)
                // Pulsación corta: Inicio normal. Si la acción fallara, se abre el Inicio con un intent.
                if (!homeLongFired && !performGlobalAction(GLOBAL_ACTION_HOME)) {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                homeLongFired = false
            }
        }
        return true
    }

    private fun openControlCenter() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_CONTROL_CENTER)
                    // Si TV-Os ya está en pantalla, el botón abre o cierra el panel.
                    .putExtra(MainActivity.EXTRA_TOGGLE, MainActivity.isVisible)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "TvOs"
        private const val LONG_PRESS_MS = 600L

        @Volatile
        private var instance: TvOsAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /** Apaga la pantalla como el botón de encendido (Android 9+). */
        fun lockScreen(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
