package com.focusbyrj.app.util.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Device/service metadata only. Never accepts audio, transcript text, or saved content. */
internal object SpeechServiceDiagnostics {
    fun describe(context: Context, error: Int?, connected: Boolean): String {
        fun read(block: () -> String): String = runCatching(block).getOrDefault("unavailable")
        val advertised = read {
            (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)).toString()
        }
        // Public resource lookup is diagnostic only: OEMs may omit it, and a temporary
        // system override can differ from this firmware default. Never route by it.
        val configured = read {
            val id = context.resources.getIdentifier("config_defaultOnDeviceSpeechRecognitionService", "string", "android")
            if (id == 0) "not exposed" else context.resources.getString(id).ifBlank { "none" }
        }
        val default = read {
            Settings.Secure.getString(context.contentResolver, "voice_recognition_service") ?: "none"
        }
        val services = read {
            @Suppress("DEPRECATION")
            val found = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            found.mapNotNull { it.serviceInfo }.map { "${it.packageName}/${it.name}" }
                .distinct().sorted().take(12).joinToString("\n").ifBlank { "none visible" }
        }
        return """
            Ayva speech diagnostics
            Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Mode: default phone speech service; provider may use online recognition
            Error: ${error ?: "no numeric code"}; provider responded: $connected
            On-device advertised: $advertised
            Firmware offline component: $configured
            Default phone component: $default
            Enabled recognition services:
            $services
        """.trimIndent()
    }
}
