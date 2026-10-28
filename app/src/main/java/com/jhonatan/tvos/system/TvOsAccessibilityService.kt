package com.jhonatan.tvos.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.jhonatan.tvos.MainActivity

/**
 * Servicio de accesibilidad mínimo:
 * - Botón ≡ del mando → Centro de control, desde cualquier app (Xiaomi no entrega esa tecla a las apps).
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
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "a11y key ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})")
        }
        if (event.keyCode != KeyEvent.KEYCODE_MENU) return false
        if (event.action == KeyEvent.ACTION_UP) {
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_CONTROL_CENTER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "TvOs"

        @Volatile
        private var instance: TvOsAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /** Apaga la pantalla como el botón de encendido (Android 9+). */
        fun lockScreen(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
