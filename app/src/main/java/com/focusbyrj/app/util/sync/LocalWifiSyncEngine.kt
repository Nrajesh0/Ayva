/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.util.sync

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Local Wi-Fi Direct E2EE Sync Server & Client.
 *
 * Supports bidirectional communication:
 * 1. Android scans PC's QR code -> Android pushes/pulls directly from PC Web app.
 * 2. Or Android generates QR -> PC connects to Android.
 */
object LocalWifiSyncEngine {

    private const val DEFAULT_PORT = 8998
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var currentSessionKey: String = ""

    data class ServerStatus(
        val isRunning: Boolean,
        val localIp: String?,
        val port: Int,
        val sessionKey: String,
        val pairingUrl: String?
    )

    data class ParsedSyncQrPayload(
        val url: String?,
        val ip: String?,
        val port: Int?,
        val key: String?,
        val passphrase: String?,
        val syncEndpoint: String? = null,
        val sessionId: String? = null,
        val pinCode: String? = null,
        val appName: String? = null
    )

    /**
     * Parses a scanned QR string payload (JSON format, RuN_Notes_Desktop schema, web deep links, or direct URLs).
     */
    fun parseQrPayload(rawContent: String): ParsedSyncQrPayload {
        val trimmed = rawContent.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val obj = JSONObject(trimmed)
                val app = obj.optString("app")
                val syncEndpoint = obj.optString("syncEndpoint").takeIf { it.isNotBlank() }
                val sessionId = obj.optString("sessionId").takeIf { it.isNotBlank() }
                val pinCode = obj.optString("pinCode").takeIf { it.isNotBlank() }

                val url = syncEndpoint ?: obj.optString("url").takeIf { it.isNotBlank() }
                val key = sessionId ?: obj.optString("key").takeIf { it.isNotBlank() }
                val passphrase = pinCode ?: obj.optString("passphrase").takeIf { it.isNotBlank() }

                return ParsedSyncQrPayload(
                    url = url,
                    ip = obj.optString("ip").takeIf { it.isNotBlank() },
                    port = if (obj.has("port")) obj.getInt("port") else null,
                    key = key,
                    passphrase = passphrase,
                    syncEndpoint = syncEndpoint,
                    sessionId = sessionId,
                    pinCode = pinCode,
                    appName = app.takeIf { it.isNotBlank() }
                )
            } catch (_: Exception) {}
        }

        // Direct URL or Deep Link fallback
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                val uri = URL(trimmed)
                val query = uri.query
                var sessionId: String? = null
                var pinCode: String? = null

                if (!query.isNullOrBlank()) {
                    query.split("&").forEach { param ->
                        val parts = param.split("=")
                        if (parts.size == 2) {
                            if (parts[0] == "syncSession" || parts[0] == "sessionId") sessionId = parts[1]
                            if (parts[0] == "pin" || parts[0] == "pinCode") pinCode = parts[1]
                        }
                    }
                }

                val endpoint = if (trimmed.contains("?")) trimmed.substringBefore("?") else trimmed
                val finalSyncEndpoint = if (endpoint.endsWith("/api/sync/push")) endpoint
                                        else if (endpoint.endsWith("/api/vault") || endpoint.endsWith("/vault")) endpoint
                                        else "${endpoint.removeSuffix("/")}/api/sync/push"

                return ParsedSyncQrPayload(
                    url = finalSyncEndpoint,
                    ip = null,
                    port = null,
                    key = sessionId,
                    passphrase = pinCode,
                    syncEndpoint = finalSyncEndpoint,
                    sessionId = sessionId,
                    pinCode = pinCode,
                    appName = "RuN_Notes_Desktop"
                )
            } catch (_: Exception) {
                return ParsedSyncQrPayload(url = trimmed, ip = null, port = null, key = null, passphrase = null)
            }
        }
        return ParsedSyncQrPayload(null, null, null, null, null)
    }

    fun normalizeTargetUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        try {
            val uri = URL(trimmed)
            if (uri.port == -1 && (uri.host.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) || uri.host == "localhost")) {
                val path = uri.path ?: ""
                return "http://${uri.host}:3000$path"
            }
        } catch (_: Exception) {}
        return trimmed
    }

    /**
     * Extracts the base server URL (protocol + host + port), stripping any trailing API endpoint paths.
     */
    fun getBaseServerUrl(rawUrl: String): String {
        var url = normalizeTargetUrl(rawUrl)
        if (url.isBlank()) return ""

        val suffixes = listOf(
            "/api/sync/push", "/api/sync/push/",
            "/api/vault", "/api/vault/",
            "/vault", "/vault/"
        )
        for (suffix in suffixes) {
            if (url.endsWith(suffix)) {
                url = url.substring(0, url.length - suffix.length)
                break
            }
        }
        return url.removeSuffix("/")
    }

    fun getVaultEndpointUrl(rawUrl: String): String {
        val baseUrl = getBaseServerUrl(rawUrl)
        return if (baseUrl.isBlank()) "" else "$baseUrl/api/vault"
    }

    fun getPushEndpointUrl(rawUrl: String): String {
        val baseUrl = getBaseServerUrl(rawUrl)
        return if (baseUrl.isBlank()) "" else "$baseUrl/api/sync/push"
    }

    /**
     * Pushes a Note or Task item to the Desktop Web App sync endpoint (/api/sync/push).
     */
    suspend fun pushDesktopSyncItem(
        syncEndpoint: String,
        sessionId: String,
        pinCode: String,
        type: String, // "NOTE" or "TASK"
        itemPayload: JSONObject
    ): Result<String> = withContext(Dispatchers.IO) {
        val endpointUrl = getPushEndpointUrl(syncEndpoint)
        try {
            val bodyJson = JSONObject().apply {
                put("sessionId", sessionId)
                put("pinCode", pinCode)
                put("type", type.uppercase())
                put("deviceInfo", "Android (${android.os.Build.MODEL})")
                put("payload", itemPayload)
            }

            val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            conn.outputStream.bufferedWriter().use { it.write(bodyJson.toString()) }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(resp.ifBlank { "OK" })
            } else {
                val errText = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                Result.failure(Exception("HTTP $responseCode on $endpointUrl: ${conn.responseMessage} ${errText.take(100)}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed connecting to $endpointUrl: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Pulls encrypted vault package from the target endpoint (e.g. PC web server).
     */
    suspend fun fetchVaultFromPc(targetUrl: String, sessionKey: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = getVaultEndpointUrl(targetUrl)
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 15000
                if (!sessionKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $sessionKey")
                    setRequestProperty("X-Auth-Token", sessionKey)
                }
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(text)
            } else {
                val errText = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                Result.failure(Exception("HTTP $responseCode on $endpoint: ${conn.responseMessage} ${errText.take(100)}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed connecting to $endpoint: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Pushes current encrypted vault package to target endpoint (e.g. PC web server).
     */
    suspend fun pushVaultToPc(targetUrl: String, encryptedPayload: String, sessionKey: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = getVaultEndpointUrl(targetUrl)
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (!sessionKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $sessionKey")
                    setRequestProperty("X-Auth-Token", sessionKey)
                }
            }

            conn.outputStream.bufferedWriter().use { it.write(encryptedPayload) }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(resp)
            } else {
                val errText = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                Result.failure(Exception("HTTP $responseCode on $endpoint: ${conn.responseMessage} ${errText.take(100)}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed connecting to $endpoint: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Resolves the primary local IPv4 address on the current Wi-Fi network.
     */
    fun getLocalWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Generates a QR Code bitmap from a payload string using ZXing.
     */
    suspend fun generateQrBitmap(
        content: String,
        widthPx: Int = 512,
        heightPx: Int = 512,
        darkColor: Color = Color.Black,
        lightColor: Color = Color.White
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                widthPx,
                heightPx,
                hints
            )
            val dark = darkColor.toArgb()
            val light = lightColor.toArgb()
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            for (x in 0 until widthPx) {
                for (y in 0 until heightPx) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) dark else light)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Starts local Wi-Fi sync server.
     */
    suspend fun startServer(
        passphrase: String,
        onVaultReceived: suspend (encryptedJson: String) -> Pair<Int, Int>,
        provideCurrentVault: suspend () -> String
    ): ServerStatus = withContext(Dispatchers.IO) {
        stopServer()

        val localIp = getLocalWifiIpAddress() ?: "127.0.0.1"
        val sessionKey = VaultCryptoEngine.generate12WordMnemonic().take(4).joinToString("-")
        currentSessionKey = sessionKey

        try {
            val socket = ServerSocket(DEFAULT_PORT)
            serverSocket = socket
            isRunning.set(true)

            Thread {
                while (isRunning.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        handleClient(client, onVaultReceived, provideCurrentVault)
                    } catch (_: Exception) {
                        break
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

            val pairingUrl = "http://$localIp:$DEFAULT_PORT"
            ServerStatus(
                isRunning = true,
                localIp = localIp,
                port = DEFAULT_PORT,
                sessionKey = sessionKey,
                pairingUrl = pairingUrl
            )
        } catch (e: Exception) {
            ServerStatus(
                isRunning = false,
                localIp = localIp,
                port = DEFAULT_PORT,
                sessionKey = "",
                pairingUrl = null
            )
        }
    }

    private fun handleClient(
        client: Socket,
        onVaultReceived: suspend (String) -> Pair<Int, Int>,
        provideCurrentVault: suspend () -> String
    ) {
        Thread {
            try {
                client.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()), true)

                val requestLine = reader.readLine() ?: return@Thread
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@Thread

                val method = parts[0]
                val path = parts[1]

                var contentLength = 0
                var headerLine: String? = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    headerLine = reader.readLine()
                }

                // Handle CORS pre-flight
                if (method.equals("OPTIONS", ignoreCase = true)) {
                    writer.print("HTTP/1.1 204 No Content\r\n")
                    writer.print("Access-Control-Allow-Origin: *\r\n")
                    writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
                    writer.print("Access-Control-Allow-Headers: Content-Type, Authorization, X-Auth-Token\r\n\r\n")
                    writer.flush()
                    return@Thread
                }

                when {
                    path == "/status" || path == "/ping" -> {
                        val json = JSONObject().apply {
                            put("status", "ok")
                            put("app", "Focus by RJ")
                            put("version", "2.0")
                            put("syncReady", true)
                        }
                        sendResponse(writer, 200, "application/json", json.toString())
                    }
                    path.startsWith("/api/vault") && method.equals("GET", ignoreCase = true) -> {
                        val encVault = kotlinx.coroutines.runBlocking { provideCurrentVault() }
                        sendResponse(writer, 200, "application/json", encVault)
                    }
                    path.startsWith("/api/vault") && method.equals("POST", ignoreCase = true) -> {
                        val bodyChars = CharArray(contentLength)
                        var readTotal = 0
                        while (readTotal < contentLength) {
                            val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                            if (r == -1) break
                            readTotal += r
                        }
                        val body = String(bodyChars, 0, readTotal)
                        val counts = kotlinx.coroutines.runBlocking { onVaultReceived(body) }
                        val resp = JSONObject().apply {
                            put("success", true)
                            put("notesImported", counts.first)
                            put("tasksImported", counts.second)
                        }
                        sendResponse(writer, 200, "application/json", resp.toString())
                    }
                    else -> {
                        sendResponse(writer, 404, "text/plain", "Endpoint not found")
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    client.close()
                } catch (_: Exception) {}
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun sendResponse(writer: PrintWriter, statusCode: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode OK\r\n")
        writer.print("Content-Type: $contentType; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Headers: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    fun stopServer() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
