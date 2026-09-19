package com.focusbyrj.app.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object LicenseManager {
    private const val TAG = "LicenseManager"
    private const val PREFS_NAME = "focus_license_prefs"
    private const val KEY_IS_PRO = "is_pro_unlocked"

    private const val PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbhqIXOUmcNrhrV06LnVfkHHbEB8n4QaQIFHU2FHPUz60wY1n+qeEmL9ZoqCErldiqxg7Rl26hHY7sE+WM2sOWg=="

    private val _isProFlow = MutableStateFlow(false)
    val isProFlow: StateFlow<Boolean> = _isProFlow.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isProFlow.value = prefs?.getBoolean(KEY_IS_PRO, false) ?: false
    }

    fun verifyAndActivateLicense(licenseKey: String): Boolean {
        val parts = licenseKey.trim().split(".")
        if (parts.size != 2) return false

        return try {
            val payloadBytes = decodeBase64Url(parts[0])
            val signatureBytes = decodeBase64Url(parts[1])

            val keyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))

            val verifier = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(payloadBytes)
            }

            if (verifier.verify(signatureBytes)) {
                val payload = String(payloadBytes, Charsets.UTF_8)
                if (payload == "PRO_LIFETIME") {
                    activatePro()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify license key", e)
            false
        }
    }

    private fun activatePro() {
        prefs?.edit()?.putBoolean(KEY_IS_PRO, true)?.apply()
        _isProFlow.value = true
        FocusEconomyManager.unlockProMax()
    }

    private fun decodeBase64Url(base64Url: String): ByteArray {
        var formatted = base64Url.replace('-', '+').replace('_', '/')
        val padLength = (4 - (formatted.length % 4)) % 4
        formatted += "=".repeat(padLength)
        return Base64.decode(formatted, Base64.DEFAULT)
    }
}
