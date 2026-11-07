package com.focusbyrj.app.util.backup

import androidx.core.util.AtomicFile
import java.io.File

/** Storage codec only. It has no database, vault, archive or application-state access. */
internal object EncryptedSnapshotFile {
    @Synchronized
    fun write(file: File, content: String) = MaintenanceGate.write {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            val encrypted = SnapshotCryptoEngine.encryptSnapshot(content.toByteArray(Charsets.UTF_8))
            output.write(encrypted)
            output.fd.sync()
            atomic.finishWrite(output)
            check(file.isFile && file.length() == encrypted.size.toLong() && file.readBytes().contentEquals(encrypted)) {
                "Encrypted snapshot installation verification failed"
            }
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    @Synchronized
    fun read(file: File): String {
        val bytes = AtomicFile(file).openRead().use { it.readBytes() }
        return if (SnapshotCryptoEngine.isEncrypted(bytes)) {
            SnapshotCryptoEngine.decryptSnapshot(bytes).toString(Charsets.UTF_8)
        } else throw SecurityException("Unsupported snapshot format; original file retained")
    }
}
