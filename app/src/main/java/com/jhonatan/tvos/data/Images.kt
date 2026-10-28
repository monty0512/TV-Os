package com.jhonatan.tvos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cargador de imágenes mínimo (sin librerías): content://, android.resource://, file:// y http(s).
 * Decodifica a 16 bits y al tamaño justo; guarda pocas imágenes para no gastar RAM en el MiTV.
 */
object Images {
    private const val MAX_BYTES = 6 * 1024 * 1024
    private val cache = LruCache<String, ImageBitmap>(5)
    private val failed = java.util.Collections.synchronizedSet(HashSet<String>())

    suspend fun load(context: Context, uri: String, maxWidth: Int, maxHeight: Int): ImageBitmap? {
        val key = "$uri@$maxWidth"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = open(context, uri)?.use { it.readCapped() } ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                    ?: error("no se pudo decodificar (${bytes.size} bytes)")
            }.onFailure {
                if (failed.add(uri)) Log.w("TvOs", "imagen: ${it.javaClass.simpleName} ${it.message} · $uri")
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /** Se llama cuando Android pide memoria. */
    fun clear() = cache.evictAll()

    private fun open(context: Context, uri: String): InputStream? {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme?.lowercase()) {
            "http", "https" -> (URL(uri).openConnection() as HttpURLConnection).run {
                connectTimeout = 5_000
                readTimeout = 8_000
                inputStream
            }
            else -> context.contentResolver.openInputStream(parsed)
        }
    }

    private fun InputStream.readCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) error("imagen demasiado grande")
        }
        return out.toByteArray()
    }

    private fun sampleSize(w: Int, h: Int, maxW: Int, maxH: Int): Int {
        var size = 1
        while (w / (size * 2) >= maxW && h / (size * 2) >= maxH) size *= 2
        return size
    }
}
