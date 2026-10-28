package com.jhonatan.tvos.system

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Servidor web mínimo para recibir una foto desde el móvil (pantalla 8 del diseño).
 * Solo funciona mientras la pantalla está abierta, solo en la red local y exige el código de 4 dígitos
 * que muestra la TV. Una conexión a la vez; sin librerías.
 */
class WallpaperServer(
    private val code: String,
    private val onPhoto: (ByteArray) -> Boolean,
) {
    private var server: ServerSocket? = null
    private var job: Job? = null

    /** Puerto en el que quedó escuchando, o null si no se pudo abrir. */
    var port: Int? = null
        private set

    fun start(scope: CoroutineScope) {
        val socket = PORTS.firstNotNullOfOrNull { p -> runCatching { ServerSocket(p) }.getOrNull() } ?: return
        socket.soTimeout = 1_000
        server = socket
        port = socket.localPort
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    socket.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: SocketException) {
                    break
                }
                runCatching { client.use { handle(it) } }.onFailure { Log.w(TAG, "fondo: ${it.message}") }
            }
        }
    }

    fun stop() {
        job?.cancel()
        runCatching { server?.close() }
        server = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val (method, path) = requestLine.split(' ').let { it.getOrNull(0) to it.getOrNull(1) }
        val response = when {
            method == "GET" && (path == "/" || path?.startsWith("/?") == true) -> page(PAGE_FORM)
            method == "POST" && path == "/upload" -> upload(input, headers)
            else -> page(message("No encontrado"), "404 Not Found")
        }
        client.getOutputStream().apply {
            write(response)
            flush()
        }
    }

    private fun upload(input: InputStream, headers: Map<String, String>): ByteArray {
        val length = headers["content-length"]?.toIntOrNull() ?: return page(message("Petición inválida"), "400 Bad Request")
        if (length > MAX_UPLOAD) return page(message("La foto es demasiado grande (máx. 15 MB)"), "413 Payload Too Large")
        val boundary = headers["content-type"]?.substringAfter("boundary=", "")?.trim('"')
            ?.takeIf { it.isNotEmpty() } ?: return page(message("Petición inválida"), "400 Bad Request")
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val parts = multipart(body.copyOf(read), boundary)
        if (parts["code"]?.toString(Charsets.UTF_8)?.trim() != code) {
            return page(message("Código incorrecto. Revisa el código que aparece en la TV."), "403 Forbidden")
        }
        val photo = parts["photo"]?.takeIf { it.isNotEmpty() }
            ?: return page(message("No se recibió ninguna foto"), "400 Bad Request")
        return if (onPhoto(photo)) {
            page(message("¡Listo! La foto ya es el fondo de tu perfil en la TV."))
        } else {
            page(message("No se pudo leer la foto. Prueba con otra en formato JPG o PNG."), "400 Bad Request")
        }
    }

    /** Partes de un formulario multipart/form-data: nombre del campo → contenido. */
    private fun multipart(body: ByteArray, boundary: String): Map<String, ByteArray> {
        val delimiter = "--$boundary".toByteArray()
        val result = HashMap<String, ByteArray>()
        var start = indexOf(body, delimiter, 0)
        while (start >= 0) {
            val headerStart = start + delimiter.size + 2 // salta "\r\n"
            val headerEnd = indexOf(body, "\r\n\r\n".toByteArray(), headerStart)
            if (headerEnd < 0) break
            val next = indexOf(body, delimiter, headerEnd)
            if (next < 0) break
            val partHeaders = String(body, headerStart, headerEnd - headerStart, Charsets.UTF_8)
            val name = Regex("name=\"([^\"]+)\"").find(partHeaders)?.groupValues?.get(1)
            val dataEnd = (next - 2).coerceAtLeast(headerEnd + 4) // quita el "\r\n" previo al delimitador
            if (name != null) result[name] = body.copyOfRange(headerEnd + 4, dataEnd)
            start = next
        }
        return result
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString("UTF-8")
            if (b == '\n'.code) return out.toString("UTF-8").trimEnd('\r')
            out.write(b)
            if (out.size() > 8_192) return null
        }
    }

    private fun page(html: String, status: String = "200 OK"): ByteArray {
        val body = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        return head.toByteArray(Charsets.US_ASCII) + body
    }

    companion object {
        private const val TAG = "TvOs"
        private const val MAX_UPLOAD = 15 * 1024 * 1024
        private val PORTS = listOf(8080, 8081, 8082, 8090)

        /** IPv4 de la TV en la red local (Wi-Fi o cable). */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()

        private val STYLE = "<style>body{font-family:system-ui,sans-serif;background:#0b0b0d;color:#f5f5f7;" +
            "margin:0;padding:24px;max-width:480px}h1{font-size:24px}label{display:block;margin:18px 0 6px;color:#c7c7cc}" +
            "input{font-size:18px;width:100%;box-sizing:border-box;padding:12px;border-radius:12px;border:1px solid #48484a;" +
            "background:#1c1c1e;color:#f5f5f7}button{margin-top:24px;width:100%;padding:14px;font-size:18px;font-weight:600;" +
            "border:0;border-radius:12px;background:#0a84ff;color:#fff}p{color:#c7c7cc;line-height:1.5}</style>"

        private val PAGE_FORM = "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>TV-Os · Fondo</title>$STYLE</head><body>" +
            "<h1>Enviar fondo a la TV</h1><p>La foto se recorta a formato de pantalla (16:9) y queda como fondo de tu perfil.</p>" +
            "<form method=\"post\" action=\"/upload\" enctype=\"multipart/form-data\">" +
            "<label for=\"code\">Código que aparece en la TV</label>" +
            "<input id=\"code\" name=\"code\" inputmode=\"numeric\" maxlength=\"4\" required autocomplete=\"off\">" +
            "<label for=\"photo\">Foto</label><input id=\"photo\" name=\"photo\" type=\"file\" accept=\"image/*\" required>" +
            "<button type=\"submit\">Enviar a la TV</button></form></body></html>"

        /** El CSS lleva "%", así que el texto se inserta con replace y no con format. */
        private fun message(text: String) = PAGE_MESSAGE.replace("{msg}", text)

        private val PAGE_MESSAGE = "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>TV-Os</title>$STYLE</head><body>" +
            "<h1>{msg}</h1><p><a href=\"/\" style=\"color:#0a84ff\">Volver</a></p></body></html>"
    }
}
