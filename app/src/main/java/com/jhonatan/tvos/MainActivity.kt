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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_ALL_APPS) controlCenterEvents++
        setContent { TvOsRoot(homeEvents, controlCenterEvents, foreground) }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
    }

    override fun onStop() {
        super.onStop()
        foreground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_ALL_APPS) controlCenterEvents++ else homeEvents++
    }
}
