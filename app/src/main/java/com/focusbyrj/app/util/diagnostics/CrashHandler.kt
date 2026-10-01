/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.focusbyrj.app.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global Uncaught Exception Handler that guarantees crash reports and stack traces
 * are synchronously persisted to disk before Android terminates the app process.
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            recordCrashToDisk(thread, throwable)
            AppLogger.flushSync()
        } catch (t: Throwable) {
            Log.e("CrashHandler", "Failed writing crash log to disk", t)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun recordCrashToDisk(thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, "diagnostics").apply { if (!exists()) mkdirs() }
        val crashFile = File(dir, "last_crash.txt")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

        val report = buildString {
            appendLine("=================================================================")
            appendLine("                  AYVA / FOCUS CRASH REPORT                     ")
            appendLine("=================================================================")
            appendLine("Timestamp       : $timestamp")
            appendLine("App Version     : ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
            appendLine("Build Type      : ${BuildConfig.BUILD_TYPE}")
            appendLine("Device Model    : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android OS      : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread          : ${thread.name} (id=${thread.id}, state=${thread.state})")
            appendLine("Exception Class : ${throwable.javaClass.name}")
            appendLine("Exception Msg   : ${AppLogger.sanitize(throwable.message ?: "")}")
            appendLine("-----------------------------------------------------------------")
            appendLine("STACK TRACE:")
            appendLine(AppLogger.sanitize(Log.getStackTraceString(throwable)))
            appendLine("-----------------------------------------------------------------")
            appendLine("RECENT APP EVENTS (PRE-CRASH TRAIL):")
            val recentLogs = AppLogger.getRecentLogsSnapshot().takeLast(60)
            if (recentLogs.isEmpty()) {
                appendLine("(No recent events captured in memory)")
            } else {
                recentLogs.forEach { logLine ->
                    appendLine(logLine)
                }
            }
            appendLine("=================================================================")
        }

        FileWriter(crashFile, false).use { writer ->
            writer.write(report)
        }
    }

    companion object {
        fun init(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, defaultHandler))
                AppLogger.i("CrashHandler", "UncaughtExceptionHandler registered successfully")
            }
        }
    }
}
