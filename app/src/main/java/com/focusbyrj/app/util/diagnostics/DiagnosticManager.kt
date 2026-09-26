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

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.content.FileProvider
import com.focusbyrj.app.BuildConfig
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.service.FocusDeviceAdminReceiver
import com.focusbyrj.app.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Orchestrates device telemetry gathering, logcat capture, log packaging into a secure ZIP,
 * and Android native FileProvider sharing.
 */
object DiagnosticManager {

    fun isRecordingEnabled(): Boolean = AppLogger.isRecording()

    fun setRecordingEnabled(enabled: Boolean) {
        AppLogger.setRecordingEnabled(enabled)
    }

    fun getLogsDirectory(context: Context): File {
        return File(context.filesDir, "diagnostics").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getExportsDirectory(context: Context): File {
        return File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getDiagnosticStorageBytes(context: Context): Long {
        var total = 0L
        val dir = getLogsDirectory(context)
        dir.listFiles()?.forEach { file ->
            total += file.length()
        }
        val exportDir = getExportsDirectory(context)
        exportDir.listFiles { _, name -> name.startsWith("FocusByRJ_Diagnostics_") && name.endsWith(".zip") }?.forEach { zip ->
            total += zip.length()
        }
        return total
    }

    fun getFormattedDiagnosticStorage(context: Context): String {
        val bytes = getDiagnosticStorageBytes(context)
        return when {
            bytes <= 0L -> "0 B"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024.0 * 1024.0))
        }
    }

    fun clearAllLogs(context: Context) {
        val dir = getLogsDirectory(context)
        dir.listFiles()?.forEach { it.delete() }

        val exportDir = getExportsDirectory(context)
        exportDir.listFiles { _, name -> name.startsWith("FocusByRJ_Diagnostics_") && name.endsWith(".zip") }?.forEach {
            it.delete()
        }
        AppLogger.i("DiagnosticManager", "All stored diagnostic logs and bundles cleared.")
    }

    /**
     * Builds complete environment and device state snapshot.
     */
    fun captureEnvironmentInfo(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComp = ComponentName(context, FocusDeviceAdminReceiver::class.java)
        val isAdminActive = dpm?.isAdminActive(adminComp) ?: false

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = pm?.isPowerSaveMode ?: false

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val netType = when {
            caps == null -> "OFFLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }

        val internalFiles = context.filesDir
        val freeInternalMB = internalFiles.freeSpace / (1024 * 1024)
        val totalInternalMB = internalFiles.totalSpace / (1024 * 1024)

        return buildString {
            appendLine("=================================================================")
            appendLine("              AYVA / FOCUS ENVIRONMENT & TELEMETRY              ")
            appendLine("=================================================================")
            appendLine("Export Timestamp   : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            appendLine("App ID             : ${BuildConfig.APPLICATION_ID}")
            appendLine("App Version        : ${BuildConfig.VERSION_NAME} (Code: ${BuildConfig.VERSION_CODE})")
            appendLine("Build Type         : ${BuildConfig.BUILD_TYPE} (Debug: ${BuildConfig.DEBUG})")
            appendLine("-----------------------------------------------------------------")
            appendLine("DEVICE HARDWARE & OS:")
            appendLine("Manufacturer       : ${Build.MANUFACTURER}")
            appendLine("Brand / Model      : ${Build.BRAND} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android OS Version : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Build Fingerprint  : ${Build.FINGERPRINT}")
            appendLine("Supported ABIs     : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("-----------------------------------------------------------------")
            appendLine("SYSTEM PERMISSIONS & INTEGRITY:")
            appendLine("Usage Stats Perm   : ${PermissionUtils.hasUsageStatsPermission(context)}")
            appendLine("Overlay Window Perm: ${PermissionUtils.hasOverlayPermission(context)}")
            appendLine("Battery Opt Ignored: ${PermissionUtils.isIgnoringBatteryOptimizations(context)}")
            appendLine("Notifications Perm : ${PermissionUtils.hasNotificationPermission(context)}")
            appendLine("Device Admin Active: $isAdminActive")
            appendLine("Vault Protection   : ${ArchiveVaultSecurity.getVaultStatus(context)}")
            appendLine("Vault Recovery Set : ${ArchiveVaultSecurity.isRecoveryConfigured(context)}")
            appendLine("-----------------------------------------------------------------")
            appendLine("RESOURCE & HEALTH STATUS:")
            appendLine("RAM Available / Tot: ${memInfo.availMem / (1024 * 1024)} MB / ${memInfo.totalMem / (1024 * 1024)} MB")
            appendLine("Low Memory Warning : ${memInfo.lowMemory} (Threshold: ${memInfo.threshold / (1024 * 1024)} MB)")
            appendLine("Internal Storage   : $freeInternalMB MB free / $totalInternalMB MB total")
            appendLine("Network Connection : $netType (Metered: ${cm?.isActiveNetworkMetered == true})")
            appendLine("Battery Level / Pwr: $batteryPct% (Charging: $isCharging, PowerSave: $isPowerSave)")
            appendLine("=================================================================")
        }
    }

    /**
     * Reads recent logcat output for this application process.
     */
    fun captureLogcat(): String {
        return try {
            val myPid = Process.myPid().toString()
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "*:V"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.contains(myPid) || l.contains("FocusByRj", ignoreCase = true) || l.contains("Ayva", ignoreCase = true)) {
                    lines.add(AppLogger.sanitize(l))
                }
            }
            reader.close()
            process.destroy()

            val tailLines = lines.takeLast(1500)
            if (tailLines.isEmpty()) {
                "(No recent process logcat entries captured)"
            } else {
                tailLines.joinToString("\n")
            }
        } catch (t: Throwable) {
            "Failed capturing logcat: ${t.message}"
        }
    }

    /**
     * Bundles environment telemetry, application event logs, crash logs, and logcat
     * into a single secure ZIP file ready for export.
     */
    suspend fun createDiagnosticZip(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.flushSync()

            val exportDir = getExportsDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(exportDir, "FocusByRJ_Diagnostics_$timeStamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // 1. Environment Info
                val envInfo = captureEnvironmentInfo(context)
                addTextEntryToZip(zos, "environment_info.txt", envInfo)

                // 2. System Logcat
                val logcatDump = captureLogcat()
                addTextEntryToZip(zos, "system_logcat.txt", logcatDump)

                // 3. Last Crash (if exists)
                val crashFile = File(getLogsDirectory(context), "last_crash.txt")
                if (crashFile.exists() && crashFile.length() > 0) {
                    addFileEntryToZip(zos, "last_crash.txt", crashFile)
                }

                // 4. In-App Events Log (merge prev + current)
                val prevLog = File(getLogsDirectory(context), "app_events_prev.log")
                val currLog = File(getLogsDirectory(context), "app_events_current.log")

                if (prevLog.exists() || currLog.exists()) {
                    zos.putNextEntry(ZipEntry("app_events.log"))
                    if (prevLog.exists()) {
                        FileInputStream(prevLog).use { it.copyTo(zos) }
                    }
                    if (currLog.exists()) {
                        FileInputStream(currLog).use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                } else {
                    // Include in-memory ring buffer if disk logging was just enabled
                    val memoryTrail = AppLogger.getRecentLogsSnapshot().joinToString("\n")
                    addTextEntryToZip(zos, "app_events.log", if (memoryTrail.isNotBlank()) memoryTrail else "(No persistent log files recorded yet)")
                }
            }

            Result.success(zipFile)
        } catch (t: Throwable) {
            AppLogger.e("DiagnosticManager", "Failed to create diagnostic zip", t)
            Result.failure(t)
        }
    }

    private fun addTextEntryToZip(zos: ZipOutputStream, entryName: String, text: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun addFileEntryToZip(zos: ZipOutputStream, entryName: String, file: File) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    /**
     * Shares the generated diagnostic zip via Android's native Share Sheet.
     */
    fun shareDiagnosticBundle(context: Context, zipFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "FocusByRJ Diagnostic Log Bundle")
                putExtra(Intent.EXTRA_TEXT, "Attached is the FocusByRJ diagnostic log bundle for app health analysis.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Export Diagnostic Logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (t: Throwable) {
            AppLogger.e("DiagnosticManager", "Failed to launch share chooser", t)
        }
    }
}
