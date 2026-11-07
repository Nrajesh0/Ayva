/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
 * 4. Automatic secret & PII sanitization (JWTs, tokens, BIP-39 recovery phrases, PINs).
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
    // Quoted values can contain spaces/newlines. For unquoted fields retain only
    // the label: logging less context is preferable to retaining part of a secret.
    private val PIN_OR_SECRET_REGEX = Regex(
        """(?i)(\b(?:passcode|pin|password|secret|apikey|api_key|access_token|auth_token|token|passphrase|mnemonic|recovery[_ -]?key|backup[_ -]?key|credential)\b["']?\s*(?:[:=]\s*|\s+))(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\r\n]*)"""
    )

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

    fun v(tag: String, message: String, throwable: Throwable? = null) = emit("V", tag, message, throwable)
    fun d(tag: String, message: String, throwable: Throwable? = null) = emit("D", tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null) = emit("I", tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = emit("W", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = emit("E", tag, message, throwable)

    private fun emit(level: String, tag: String, message: String, throwable: Throwable?) {
        val safeTag = sanitize(tag).take(128)
        val safeMessage = sanitize(message).take(4_000)
        val safeStack = throwable?.let { sanitize(Log.getStackTraceString(it)).take(4_000) }
        val printable = if (safeStack == null) safeMessage else "$safeMessage\n$safeStack"
        // Never pass the original Throwable to Android: its message/cause may contain credentials.
        when (level) {
            "V" -> Log.v(safeTag, printable)
            "D" -> Log.d(safeTag, printable)
            "I" -> Log.i(safeTag, printable)
            "W" -> Log.w(safeTag, printable)
            else -> Log.e(safeTag, printable)
        }
        logInternal(level, safeTag, safeMessage, safeStack)
    }

    private fun logInternal(level: String, tag: String, sanitized: String, stackTrace: String?) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val threadName = Thread.currentThread().name

        val logLine = if (stackTrace != null) {
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
     * Masks any sensitive tokens, mnemonic phrases, and PINs.
     */
    fun sanitize(input: String): String {
        var clean = input.take(65_536)
        if (clean.contains("Bearer", ignoreCase = true)) {
            clean = JWT_REGEX.replace(clean, "Bearer [REDACTED_JWT]")
        }
        if (clean.contains("eyJ", ignoreCase = false)) {
            clean = RAW_JWT_REGEX.replace(clean, "[REDACTED_KEY]")
        }
        clean = PIN_OR_SECRET_REGEX.replace(clean) { mr -> "${mr.groupValues[1]}[REDACTED]" }
        if (clean.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 12) {
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
