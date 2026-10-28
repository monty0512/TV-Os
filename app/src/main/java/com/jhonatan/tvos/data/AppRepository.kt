package com.jhonatan.tvos.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App instalada que se puede abrir desde el Inicio. */
data class TvApp(
    val packageName: String,
    val label: String,
    /**
     * Banner 16:9 listo para pintar (el de la app, o su icono sobre su color).
     * null mientras TV-Os está en segundo plano: se libera para dejar memoria a la app abierta.
     */
    val banner: ImageBitmap?,
    /** Color dominante del banner o icono: fondo del Top Shelf cuando la app no publica contenido. */
    val color: Color,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(): List<TvApp> = withContext(Dispatchers.IO) {
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0,
        )
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        val seen = HashSet<String>()
        (leanback + launcher)
            .filter { it.activityInfo.packageName != context.packageName && seen.add(it.activityInfo.packageName) }
            .mapNotNull { runCatching { build(it) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    fun launchIntent(packageName: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun build(ri: ResolveInfo): TvApp {
        val info = ri.activityInfo
        val bannerDrawable = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm)
        val icon = info.loadIcon(pm)
        val color = dominantColor(bannerDrawable ?: icon)
        val bitmap = if (bannerDrawable != null) {
            bannerDrawable.toBitmap(BANNER_W, BANNER_H, BANNER_CONFIG)
        } else {
            iconBanner(icon, color)
        }
        return TvApp(
            packageName = info.packageName,
            label = ri.loadLabel(pm).toString(),
            banner = bitmap.asImageBitmap(),
            color = color,
        )
    }

    /** Banner sintético para apps sin banner: icono centrado sobre su color dominante. */
    private fun iconBanner(icon: Drawable, color: Color): Bitmap {
        val bitmap = Bitmap.createBitmap(BANNER_W, BANNER_H, BANNER_CONFIG)
        val canvas = Canvas(bitmap)
        canvas.drawColor(darken(color, 0.55f).toArgb())
        val d = icon.constantState?.newDrawable()?.mutate() ?: icon
        val size = BANNER_H * 2 / 3
        val left = (BANNER_W - size) / 2
        val top = (BANNER_H - size) / 2
        d.setBounds(left, top, left + size, top + size)
        d.draw(canvas)
        return bitmap
    }

    /** Media de los píxeles ponderada por saturación y brillo, para evitar fondos grises. */
    private fun dominantColor(drawable: Drawable): Color {
        val small = drawable.toBitmap(24, 24)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var total = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val px = small.getPixel(x, y)
            if (android.graphics.Color.alpha(px) < 128) continue
            android.graphics.Color.colorToHSV(px, hsv)
            val w = hsv[1] * hsv[2] + 0.05
            r += android.graphics.Color.red(px) * w
            g += android.graphics.Color.green(px) * w
            b += android.graphics.Color.blue(px) * w
            total += w
        }
        if (total == 0.0) return Color(0xFF2C2C2E)
        return Color((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
    }

    companion object {
        const val BANNER_W = 320
        const val BANNER_H = 180

        /** 16 bits por píxel: la mitad de memoria que ARGB_8888; los banners son opacos. */
        val BANNER_CONFIG = Bitmap.Config.RGB_565
    }
}

fun darken(c: Color, amount: Float) = Color(
    red = c.red * (1 - amount),
    green = c.green * (1 - amount),
    blue = c.blue * (1 - amount),
)

fun lighten(c: Color, amount: Float) = Color(
    red = c.red + (1 - c.red) * amount,
    green = c.green + (1 - c.green) * amount,
    blue = c.blue + (1 - c.blue) * amount,
)
