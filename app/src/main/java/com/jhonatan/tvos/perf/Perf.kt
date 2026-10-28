package com.jhonatan.tvos.perf

import android.app.ActivityManager
import android.content.Context

/**
 * Modo de rendimiento adaptativo.
 * En equipos low-RAM (como el MiTV-MOOQ0) se reducen sombras y transiciones de color.
 */
object Perf {
    fun reducedEffects(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return true
        return am.isLowRamDevice || am.memoryClass < 128
    }
}
