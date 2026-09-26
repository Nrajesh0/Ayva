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

package com.focusbyrj.app.util.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Enterprise-grade in-app diagnostics and logging engine.
 *
 * Features:
 * 1. Non-blocking asynchronous I/O backed by Kotlin Channel & Dispatchers.IO.
 * 2. In-memory circular ring buffer of recent logs for instant crash contextualization.
 * 3. Automatic 2-file rolling log mechanism (max 2MB per file, 4MB total cap).
 * 4. Automatic secret & PII sanitization (JWTs, Supabase keys, BIP-39 recovery phrases, PINs).
 * 5. Synchronous emergency flush for uncaught crashes.
 */
object AppLogger {

    private const val PREFS_NAME = "diagnostics_prefs"
    private const val KEY_RECORDING_ENABLED = "diagnostic_recording_enabled"
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB per file
    private const val MAX_RING_BUFFER_SIZE = 500

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val ringBuffer = ArrayDeque<String>(MAX_RING_BUFFER_SIZE)
    private val ringBufferLock = Any()

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    @Volatile
    private var isRecordingEnabled: Boolean = false

    private val logChannel = Channel<String>(
        capacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val loggingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Sanitization Patterns
    private val JWT_REGEX = Regex("Bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*")
    private val RAW_JWT_REGEX = Regex("eyJ[A-Za-z0-9-_=]{20,}\\.[A-Za-z0-9-_=]{20,}\\.?[A-Za-z0-9-_.+/=]*")
    private val BIP39_MNEMONIC_REGEX = Regex("\\b(?:[a-z]{3,8}\\s+){11,23}[a-z]{3,8}\\b", RegexOption.IGNORE_CASE)
    private val PIN_OR_SECRET_REGEX = Regex("(?i)(\\b(?:passcode|pin|password|secret|apikey|mnemonic)\\b[\"':=\\s]+)([A-Za-z0-9!@#\$%^&*()_+=-]{3,})")

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isRecordingEnabled = prefs?.getBoolean(KEY_RECORDING_ENABLED, false) ?: false

        startDiskWriterWorker()
        i("AppLogger", "Diagnostic AppLogger initialized. Recording enabled: $isRecordingEnabled")
    }

    fun setRecordingEnabled(enabled: Boolean) {
        isRecordingEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_RECORDING_ENABLED, enabled)?.apply()
        i("AppLogger", "Diagnostic recording switched to: $enabled")
    }

    fun isRecording(): Boolean = isRecordingEnabled

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        Log.v(tag, message, throwable)
        logInternal("V", tag, message, throwable)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        logInternal("D", tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        logInternal("I", tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        logInternal("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        logInternal("E", tag, message, throwable)
    }

    private fun logInternal(level: String, tag: String, rawMessage: String, throwable: Throwable?) {
        val sanitized = sanitize(rawMessage)
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val threadName = Thread.currentThread().name

        val logLine = if (throwable != null) {
            val stackTrace = Log.getStackTraceString(throwable)
            "[$timestamp] [T:$threadName] [$level] [$tag]: $sanitized\n$stackTrace"
        } else {
            "[$timestamp] [T:$threadName] [$level] [$tag]: $sanitized"
        }

        // Always update in-memory ring buffer (even if disk logging is off, for crash context)
        synchronized(ringBufferLock) {
            if (ringBuffer.size >= MAX_RING_BUFFER_SIZE) {
                ringBuffer.pollFirst()
            }
            ringBuffer.addLast(logLine)
        }

        // If user enabled recording, send to background disk writer
        if (isRecordingEnabled) {
            logChannel.trySend(logLine)
        }
    }

    /**
     * Masks any sensitive tokens, Supabase keys, mnemonic phrases, and PINs.
     */
    fun sanitize(input: String): String {
        var clean = input
        if (clean.contains("Bearer", ignoreCase = true)) {
            clean = JWT_REGEX.replace(clean, "Bearer [REDACTED_JWT]")
        }
        if (clean.contains("eyJ", ignoreCase = false)) {
            clean = RAW_JWT_REGEX.replace(clean, "[REDACTED_KEY]")
        }
        if (clean.contains("passcode", ignoreCase = true) ||
            clean.contains("pin", ignoreCase = true) ||
            clean.contains("secret", ignoreCase = true) ||
            clean.contains("password", ignoreCase = true)
        ) {
            clean = PIN_OR_SECRET_REGEX.replace(clean) { mr -> "${mr.groupValues[1]}[REDACTED]" }
        }
        if (clean.split(" ").size >= 12) {
            clean = BIP39_MNEMONIC_REGEX.replace(clean, "[REDACTED_MNEMONIC]")
        }
        return clean
    }

    fun getRecentLogsSnapshot(): List<String> {
        synchronized(ringBufferLock) {
            return ringBuffer.toList()
        }
    }

    private fun startDiskWriterWorker() {
        loggingScope.launch {
            for (line in logChannel) {
                writeLineToDisk(line)
            }
        }
    }

    private fun writeLineToDisk(line: String) {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, "diagnostics").apply { if (!exists()) mkdirs() }
            val currentFile = File(dir, "app_events_current.log")

            // Check rolling limit
            if (currentFile.exists() && currentFile.length() >= MAX_FILE_SIZE_BYTES) {
                val prevFile = File(dir, "app_events_prev.log")
                if (prevFile.exists()) {
                    prevFile.delete()
                }
                currentFile.renameTo(prevFile)
            }

            FileWriter(currentFile, true).use { writer ->
                writer.append(line).append("\n")
            }
        } catch (_: Throwable) {
            // Never crash on logging I/O failure
        }
    }

    /**
     * Synchronously flushes in-memory lines and pending items immediately to disk.
     * Called during uncaught exceptions right before the process dies.
     */
    fun flushSync() {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, "diagnostics").apply { if (!exists()) mkdirs() }
            val currentFile = File(dir, "app_events_current.log")

            FileWriter(currentFile, true).use { writer ->
                // Drain any pending channel messages
                while (true) {
                    val line = logChannel.tryReceive().getOrNull() ?: break
                    writer.append(line).append("\n")
                }
                writer.flush()
            }
        } catch (_: Throwable) {
            // Silently ignore
        }
    }
}
