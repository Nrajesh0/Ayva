package com.focusbyrj.app.util.backup

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import org.json.JSONObject

/** Non-sensitive recovery outcomes survive process death; no credentials or record content. */
internal object RestoreNotice {
    private var startupNotice: Pair<String, String>? = null

    @Synchronized fun forStartup(): Pair<String, String>? = startupNotice

    private fun file(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, "restore_outcome.json"))

    @Synchronized
    private fun read(context: Context): JSONObject =
        try {
            file(context).openRead().use {
                val buffer = ByteArray(65_537)
                var size = 0
                while (size < buffer.size) {
                    val read = it.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    if (read == 0) {
                        val next = it.read()
                        if (next < 0) break
                        buffer[size++] = next.toByte()
                    } else size += read
                }
                require(size <= 65_536) { "Restore report is too large" }
                JSONObject(buffer.copyOf(size).decodeToString()).also { root ->
                    if (root.optBoolean("pending"))
                        require(root.opt("title") is String && root.opt("message") is String)
                }
            }
        } catch (error: java.io.FileNotFoundException) {
            JSONObject()
        } catch (_: Exception) {
            JSONObject()
                .put("pending", true)
                .put("title", "Restore status unavailable")
                .put(
                    "message",
                    "The last restore report could not be read. Check your data before importing copies again.",
                )
        }

    @Synchronized
    private fun write(context: Context, root: JSONObject) {
        val value = file(context)
        val stream = value.startWrite()
        try {
            stream.write(root.toString().toByteArray())
            stream.fd.sync()
            value.finishWrite(stream)
        } catch (error: Throwable) {
            value.failWrite(stream)
            throw error
        }
    }

    @Synchronized
    fun outcome(context: Context, value: String, detail: String = "") =
        write(
            context,
            read(context)
                .put("outcome", value)
                .put("detail", detail)
                .put("unseen", true)
                .put("pending", false),
        )

    @Synchronized fun lastOutcome(context: Context) = read(context).optString("outcome")

    @Synchronized
    fun publish(context: Context, title: String, message: String) =
        write(
            context,
            read(context).put("title", title).put("message", message).put("pending", true),
        )

    @Synchronized
    fun pending(context: Context): Pair<String, String>? =
        read(context).let {
            when {
                it.optBoolean("pending") -> it.getString("title") to it.getString("message")
                it.optBoolean("unseen") && it.optString("outcome") == "completed" ->
                    "Restore completed" to
                        ("Your restore finished. Do not import copies again unless you want additional copies." +
                            it.optString("detail"))
                it.optBoolean("unseen") && it.optString("outcome") == "recovered" ->
                    "Restore recovered" to
                        "Your previous records were recovered and imported files were removed. Choose the backup again to retry."
                else -> null
            }
        }

    @Synchronized
    fun onStartup(context: Context) {
        val root = read(context)
        if (
            root.optBoolean("unseen") && root.optString("outcome") in setOf("checking", "applying")
        ) {
            root
                .put("pending", true)
                .put("title", "Restore interrupted")
                .put(
                    "message",
                    "Restore did not finish. No restore is running now. Choose the backup again; keep its original file.",
                )
            write(context, root)
        }
        startupNotice = pending(context)
    }

    @Synchronized
    fun acknowledge(context: Context) {
        write(context, read(context).put("pending", false).put("unseen", false))
        startupNotice = null
    }
}
