package com.focusbyrj.app.util.backup

import android.content.Context
import android.system.Os
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Preserve abandoned old staging paths while removing their legacy plaintext-at-rest state. */
internal object RestoreStaging {
    fun protectLegacyStages(context: Context) {
        context.cacheDir.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("restore_media_stage_") }.forEach { directory ->
            directory.walkTopDown().filter { it.isFile && !it.name.endsWith(".securing") }.forEach { file ->
                if (EncryptedMediaStorage.isEncrypted(file)) return@forEach
                val temporary = File(file.parentFile, ".${UUID.randomUUID()}.securing")
                try {
                    val original = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        EncryptedMediaStorage.openEncryptedOutputStream(temporary).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer); if (count < 0) break
                                original.update(buffer, 0, count); output.write(buffer, 0, count)
                            }
                        }
                    }
                    val verified = MessageDigest.getInstance("SHA-256")
                    checkNotNull(EncryptedMediaStorage.openDecryptedInputStream(temporary)).use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) { val count = input.read(buffer); if (count < 0) break; verified.update(buffer, 0, count) }
                    }
                    check(original.digest().contentEquals(verified.digest())) { "Legacy staging verification failed" }
                    java.io.RandomAccessFile(temporary, "rw").use { it.fd.sync() }
                    Os.rename(temporary.path, file.path) // Same-directory atomic replacement, never delete-first.
                } finally { temporary.delete() }
            }
        }
    }
}
