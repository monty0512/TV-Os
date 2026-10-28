package com.jhonatan.tvos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jhonatan.tvos.ui.TvOsRoot

class MainActivity : ComponentActivity() {

    /** Se incrementa cada vez que se pulsa Inicio estando ya en TV-Os. */
    private var homeEvents by mutableIntStateOf(0)

    /**
     * Se incrementa con la pulsación larga de Inicio, si la TV la envía como "todas las apps"
     * (config_longPressOnHomeBehavior = 1). Depende del firmware; si no llega, se usa el botón ≡.
     */
    private var controlCenterEvents by mutableIntStateOf(0)

    /** false mientras otra app ocupa la pantalla. */
    private var foreground by mutableStateOf(true)

    /** Se incrementa cuando Android pide memoria con TV-Os en segundo plano. */
    private var trimEvents by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action.isControlCenter()) controlCenterEvents++
        setContent { TvOsRoot(homeEvents, controlCenterEvents, foreground, trimEvents) }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
    }

    override fun onStop() {
        super.onStop()
        foreground = false
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!foreground && level >= TRIM_MEMORY_BACKGROUND) trimEvents++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action.isControlCenter()) controlCenterEvents++ else homeEvents++
    }

    private fun String?.isControlCenter() = this == Intent.ACTION_ALL_APPS || this == ACTION_CONTROL_CENTER

    companion object {
        /** Lo envía el servicio de accesibilidad al pulsar ≡ en cualquier app. */
        const val ACTION_CONTROL_CENTER = "com.jhonatan.tvos.CONTROL_CENTER"
    }
}
