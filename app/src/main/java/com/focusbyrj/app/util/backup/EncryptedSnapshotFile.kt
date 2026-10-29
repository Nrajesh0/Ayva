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

    /** Keep detection/read/replacement under the same lock as ordinary snapshot I/O. */
    @Synchronized
    fun migratePlaintext(file: File): Boolean {
        if (SnapshotCryptoEngine.isEncrypted(file)) return false
        val content = read(file)
        // A corrupt container is not a legacy JSON snapshot.
        org.json.JSONObject(content)
        write(file, content)
        check(read(file) == content) { "Snapshot migration verification failed" }
        return true
    }

    @Synchronized
    fun read(file: File): String {
        val bytes = AtomicFile(file).openRead().use { it.readBytes() }
        return if (SnapshotCryptoEngine.isEncrypted(bytes)) {
            SnapshotCryptoEngine.decryptSnapshot(bytes).toString(Charsets.UTF_8)
        } else bytes.toString(Charsets.UTF_8)
    }
}
