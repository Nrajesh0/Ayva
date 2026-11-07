package com.focusbyrj.app.util.crypto

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** JVM tests cannot use AndroidKeyStore. Never callable in an installed application. */
internal object TestRuntimeKeys {
    val available: Boolean get() = com.focusbyrj.app.BuildConfig.DEBUG && android.os.Build.FINGERPRINT.startsWith("robolectric")
    private val keys = mutableMapOf<String, SecretKey>()
    @Synchronized fun key(domain: String): SecretKey {
        check(available) { "Software test keys are unavailable on devices" }
        return keys.getOrPut(domain) { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
    }
}
