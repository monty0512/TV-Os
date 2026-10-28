package com.jhonatan.tvos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

/** Qué se envía desde el móvil: fondo 16:9 o foto de perfil cuadrada. */
enum class PhotoKind(val folder: String, val width: Int, val height: Int) {
    BACKGROUND("backgrounds", 1280, 720),
    AVATAR("avatars", 320, 320),
}

/** Fotos enviadas desde el móvil: un JPG por perfil y tipo en el almacenamiento interno de la app. */
object Backgrounds {

    fun file(context: Context, kind: PhotoKind, profileId: String) =
        File(File(context.filesDir, kind.folder).apply { mkdirs() }, "$profileId.jpg")

    /** URI que entiende [Images.load]; la versión evita mostrar una imagen vieja de la caché. */
    fun uri(context: Context, kind: PhotoKind, profile: Profile): String? {
        val version = if (kind == PhotoKind.AVATAR) profile.avatarVersion else profile.backgroundVersion
        return if (version == 0L) null else "file://${file(context, kind, profile.id).absolutePath}?v=$version"
    }

    fun delete(context: Context, kind: PhotoKind, profileId: String) {
        file(context, kind, profileId).delete()
    }

    fun deleteAll(context: Context, profileId: String) = PhotoKind.entries.forEach { delete(context, it, profileId) }

    /**
     * Guarda la foto respetando la rotación de la cámara, recortada al centro con la proporción del tipo
     * (16:9 o cuadrada) y reducida a su tamaño. Devuelve false si los bytes no son una imagen.
     */
    fun save(context: Context, kind: PhotoKind, profileId: String, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= kind.width && bounds.outHeight / (sample * 2) >= kind.height) sample *= 2
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

        val targetRatio = kind.width.toFloat() / kind.height
        val (cw, ch) = if (bitmap.width.toFloat() / bitmap.height > targetRatio) {
            (bitmap.height * targetRatio).toInt() to bitmap.height
        } else {
            bitmap.width to (bitmap.width / targetRatio).toInt()
        }
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - cw) / 2, (bitmap.height - ch) / 2, cw, ch)
        val scaled = Bitmap.createScaledBitmap(cropped, kind.width, kind.height, true)
        file(context, kind, profileId).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return true
    }
}
