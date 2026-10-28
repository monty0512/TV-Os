package com.jhonatan.tvos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

/** Fondos de perfil enviados desde el móvil: un JPG de 1280x720 por perfil en el almacenamiento de la app. */
object Backgrounds {
    private const val WIDTH = 1280
    private const val HEIGHT = 720

    fun file(context: Context, profileId: String) = File(File(context.filesDir, "backgrounds").apply { mkdirs() }, "$profileId.jpg")

    /** URI que entiende [Images.load]; la versión evita mostrar una imagen vieja de la caché. */
    fun uri(context: Context, profile: Profile): String? =
        if (profile.backgroundVersion == 0L) null else "file://${file(context, profile.id).absolutePath}?v=${profile.backgroundVersion}"

    fun delete(context: Context, profileId: String) {
        file(context, profileId).delete()
    }

    /**
     * Guarda la foto recortada a 16:9 y reducida a 1280x720, respetando la rotación de la cámara del móvil.
     * Devuelve false si los bytes no son una imagen.
     */
    fun save(context: Context, profileId: String, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false

        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation != 0f) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        }

        // Recorte centrado a 16:9 y escalado a 1280x720.
        val targetRatio = WIDTH.toFloat() / HEIGHT
        val (cw, ch) = if (bitmap.width.toFloat() / bitmap.height > targetRatio) {
            (bitmap.height * targetRatio).toInt() to bitmap.height
        } else {
            bitmap.width to (bitmap.width / targetRatio).toInt()
        }
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - cw) / 2, (bitmap.height - ch) / 2, cw, ch)
        val scaled = Bitmap.createScaledBitmap(cropped, WIDTH, HEIGHT, true)
        file(context, profileId).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return true
    }
}
