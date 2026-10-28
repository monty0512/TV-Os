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

    /** false mientras otra app ocupa la pantalla. */
    private var foreground by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvOsRoot(homeEvents, foreground) }
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
        homeEvents++
    }
}
