package com.jhonatan.tvos.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un programa publicado por una app (canal de vista previa o "Continuar viendo"). */
data class ShelfItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val imageUri: String?,
    val intentUri: String?,
    /** 0..1 si se conoce cuánto se ha visto. */
    val progress: Float?,
)

/** Lo que muestra la fila "Continuar viendo": un programa o, si no hay, una app usada hace poco. */
sealed interface ContinueItem {
    val key: String

    data class Program(val item: ShelfItem) : ContinueItem {
        override val key = "p${item.id}"
    }

    data class RecentApp(val app: TvApp) : ContinueItem {
        override val key = "a${app.packageName}"
    }
}

/**
 * Lee los canales de vista previa y "Continuar viendo" que publican las apps (TvProvider).
 * Requiere READ_TV_LISTINGS, que concede el script de instalación. Sin el permiso devuelve listas vacías
 * y el Inicio sigue mostrando el color de cada app.
 * El proveedor no admite filtros ni orden en la consulta para apps de terceros: se filtra aquí.
 */
class TvContentRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Programas de los canales visibles, agrupados por app. */
    suspend fun previewByPackage(): Map<String, List<ShelfItem>> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyMap()
        val browsable = browsableChannels()
        val items = query(TvContract.PreviewPrograms.CONTENT_URI) { c ->
            val channel = c.long("channel_id")
            if (browsable != null && channel != null && channel !in browsable) null else c.toItem()
        }
        Log.i(TAG, "preview programs=${items.size} apps=${items.map { it.packageName }.distinct()}")
        items.groupBy { it.packageName }.mapValues { (_, list) -> list.take(MAX_PER_APP) }
    }

    /** "Continuar viendo": lo más reciente primero. */
    suspend fun watchNext(): List<ShelfItem> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val rows = query(TvContract.WatchNextPrograms.CONTENT_URI) { c ->
            c.toItem()?.let { it to (c.long("last_engagement_time_utc_millis") ?: 0L) }
        }
        Log.i(TAG, "watch next=${rows.size}")
        rows.sortedByDescending { it.second }.map { it.first }.take(MAX_WATCH_NEXT)
    }

    /** null si no se pudo leer la tabla de canales (entonces no se filtra). */
    private fun browsableChannels(): Set<Long>? = runCatching {
        query(TvContract.Channels.CONTENT_URI) { c ->
            if (c.int("browsable") == 1) c.long("_id") else null
        }.toSet()
    }.getOrNull()

    private fun <T> query(uri: Uri, map: (Cursor) -> T?): List<T> = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val out = ArrayList<T>()
            while (c.moveToNext() && out.size < MAX_ROWS) map(c)?.let(out::add)
            out
        } ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "no se pudo leer $uri: ${e.javaClass.simpleName} ${e.message}")
        emptyList()
    }

    private fun Cursor.toItem(): ShelfItem? {
        val pkg = string("package_name") ?: return null
        val title = string("title") ?: return null
        val position = long("last_playback_position_millis")
        val duration = long("duration_millis")
        return ShelfItem(
            id = long("_id") ?: return null,
            packageName = pkg,
            title = title,
            subtitle = string("episode_title") ?: string("short_description"),
            imageUri = string("poster_art_uri") ?: string("thumbnail_uri"),
            intentUri = string("intent_uri"),
            progress = if (position != null && duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
        )
    }

    private fun Cursor.index(name: String) = getColumnIndex(name).takeIf { it >= 0 }
    private fun Cursor.string(name: String) = index(name)?.let { if (isNull(it)) null else getString(it) }?.takeIf { it.isNotBlank() }
    private fun Cursor.long(name: String) = index(name)?.let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.int(name: String) = index(name)?.let { if (isNull(it)) null else getInt(it) }

    companion object {
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
        private const val TAG = "TvOs"
        private const val MAX_ROWS = 400
        private const val MAX_PER_APP = 6
        private const val MAX_WATCH_NEXT = 10

        /** Abre un programa con el intent que publicó la app. */
        fun openIntent(item: ShelfItem): Intent? = item.intentUri?.let { uri ->
            runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull()
        }
    }
}

/** Apps abiertas desde TV-Os, la más reciente primero (respaldo de "Continuar viendo"). */
object RecentApps {
    private const val PREFS = "recent_apps"
    private const val KEY = "list"
    private const val MAX = 6

    fun get(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun record(context: Context, packageName: String) {
        val list = (listOf(packageName) + get(context).filter { it != packageName }).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, list.joinToString("\n")).apply()
    }
}
