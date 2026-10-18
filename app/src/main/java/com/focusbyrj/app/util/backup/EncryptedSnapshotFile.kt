package com.focusbyrj.app.util.backup

import androidx.core.util.AtomicFile
import java.io.File

/** Storage codec only. It has no database, vault, archive or application-state access. */
internal object EncryptedSnapshotFile {
    fun write(file: File, content: String) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(SnapshotCryptoEngine.encryptSnapshot(content.toByteArray(Charsets.UTF_8)))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    fun read(file: File): String {
        val bytes = AtomicFile(file).openRead().use { it.readBytes() }
        return if (SnapshotCryptoEngine.isEncrypted(bytes)) {
            SnapshotCryptoEngine.decryptSnapshot(bytes).toString(Charsets.UTF_8)
        } else bytes.toString(Charsets.UTF_8)
    }
}
