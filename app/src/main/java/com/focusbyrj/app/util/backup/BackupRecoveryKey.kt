package com.focusbyrj.app.util.backup

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** A portable 256-bit backup credential. Never derive it from a PIN, clock, UUID or device ID. */
internal class BackupRecoveryKey private constructor(private val bytes: ByteArray) : AutoCloseable {
    private var closed = false
    val id: String get() { check(!closed); return digest("RUN backup key id v1", bytes).take(16).hex() }
    fun credential(): String { check(!closed); return "RUN-BK1-" + bytes.hex().chunked(8).joinToString("-") + "-" + checksum(bytes) }
    fun document(): String = JSONObject().put("format", "RUN-BACKUP-KEY").put("version", 1)
        .put("keyId", id).put("key", credential()).toString(2)
    override fun close() { bytes.fill(0); closed = true }
    override fun toString(): String = "BackupRecoveryKey([REDACTED])"

    companion object {
        fun generate(): BackupRecoveryKey = BackupRecoveryKey(ByteArray(32).also { SecureRandom().nextBytes(it) })
        fun parse(input: String): BackupRecoveryKey {
            try { return parseChecked(input) }
            catch (_: org.json.JSONException) {
                // JSONTokener errors can contain the entire document, including its secret.
                throw IllegalArgumentException("Invalid recovery key document. Import an intact key file or enter the printed key.")
            }
        }
        private fun parseChecked(input: String): BackupRecoveryKey {
            require(input.length <= 8192) { "Recovery key is too long." }
            val value = if (input.trimStart().startsWith("{")) {
                require(input.count { it == '{' } == 1 && input.count { it == '}' } == 1 && '[' !in input && ']' !in input) { "Invalid recovery key document." }
                val json = JSONObject(input)
                require(json.getString("format") == "RUN-BACKUP-KEY" && json.getInt("version") == 1) { "Unsupported recovery key document." }
                json.getString("key")
            } else input
            val normalized = value.filterNot { it.isWhitespace() || it == '-' }.uppercase(java.util.Locale.ROOT)
            require(normalized.startsWith("RUNBK1") && normalized.length == 78) { "Enter a complete RUN backup recovery key." }
            val payload = normalized.substring(6, 70)
            require(payload.all { it in "0123456789ABCDEF" }) { "Recovery key contains an invalid character." }
            val bytes = payload.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            try {
                require(MessageDigest.isEqual(checksum(bytes).toByteArray(), normalized.substring(70).toByteArray())) {
                    "Recovery key checksum failed. Check every group against your exported document."
                }
                if (input.trimStart().startsWith("{")) require(JSONObject(input).getString("keyId") == digest("RUN backup key id v1", bytes).take(16).hex()) { "Recovery key identifier does not match." }
                return BackupRecoveryKey(bytes.copyOf())
            } finally { bytes.fill(0) }
        }
        private fun checksum(bytes: ByteArray) = digest("RUN backup key checksum v1", bytes).take(4).hex()
        private fun digest(domain: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(domain.toByteArray() + byteArrayOf(0) + bytes)
        private fun Iterable<Byte>.hex() = joinToString("") { "%02X".format(it.toInt() and 255) }
        private fun ByteArray.hex() = asIterable().hex()
    }
}
