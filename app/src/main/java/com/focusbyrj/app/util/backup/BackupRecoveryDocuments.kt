package com.focusbyrj.app.util.backup

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Keys never enter app caches, SavedState, logs or implicit shares. Export only to the chosen document URI. */
internal object BackupRecoveryDocuments {
    private const val LIMIT = 2 * 1024 * 1024
    fun pdf(key: BackupRecoveryKey): ByteArray {
        val out = ByteArrayOutputStream()
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
            val canvas = page.canvas; canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
            canvas.drawText("RUN · Backup recovery key", 42f, 62f, paint)
            paint.typeface = Typeface.DEFAULT; paint.textSize = 12f
            val lines = listOf("Key ID: ${key.id}  ·  Format: RUN-BK1", "This document can unlock every backup made with this key.",
                "Keep it separate from your backups. Do not share it or upload it publicly.",
                "After reinstalling: Settings > Data & recovery > Restore encrypted backup.",
                "Choose Use recovery key, then import this PDF/key file or enter the key.",
                "You can restore without this phone, its screen lock or its Android Keystore.")
            lines.forEachIndexed { i, line -> canvas.drawText(line, 42f, 95f + i * 21, paint) }
            paint.typeface = Typeface.MONOSPACE; paint.textSize = 13f
            key.credential().chunked(48).forEachIndexed { i, line -> canvas.drawText(line, 42f, 255f + i * 22, paint) }
            val qr = QRCodeWriter().encode(key.credential(), BarcodeFormat.QR_CODE, 240, 240,
                mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M))
            paint.color = Color.BLACK
            for (y in 0 until qr.height) for (x in 0 until qr.width) if (qr[x,y]) canvas.drawRect(42f+x, 320f+y, 43f+x, 321f+y, paint)
            paint.typeface = Typeface.DEFAULT; paint.textSize = 11f
            canvas.drawText("The QR code contains the key itself, not a website address.", 42f, 590f, paint)
            canvas.drawText("Without your password/recovery key, encrypted backups cannot be recovered.", 42f, 615f, paint)
            canvas.drawText("Saving a key does not create a backup. Check the last successful backup in RUN.", 42f, 640f, paint)
            pdf.finishPage(page); pdf.writeTo(out)
        } finally { pdf.close() }
        // A PDF comment before startxref leaves all object/xref offsets unchanged.
        // Import reads this bounded marker; it never executes an untrusted PDF parser.
        val raw = out.toByteArray()
        try {
            val split = raw.toString(Charsets.ISO_8859_1).lastIndexOf("startxref")
            check(split > 0)
            return raw.copyOfRange(0, split) + ("%RUN-RECOVERY-KEY-V1 " + key.credential() + "\n").toByteArray() + raw.copyOfRange(split, raw.size)
        } finally { raw.fill(0) }
    }
    fun export(context: Context, uri: Uri, key: BackupRecoveryKey, asPdf: Boolean) {
        val bytes = if (asPdf) pdf(key) else key.document().toByteArray(Charsets.UTF_8)
        try {
            require(uri.scheme == "content") { "Choose a document outside the app." }
            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes); it.flush() }
            val actual = checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesBounded(LIMIT) }
            try {
                require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), MessageDigest.getInstance("SHA-256").digest(actual))) {
                    "The exported key document could not be read back correctly. Choose another destination."
                }
            } finally { actual.fill(0) }
        } finally { bytes.fill(0) }
    }
    fun read(context: Context, uri: Uri): BackupRecoveryKey {
        val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesBounded(LIMIT) }
        try {
            if (!bytes.take(4).toByteArray().contentEquals("%PDF".toByteArray())) return BackupRecoveryKey.parse(bytes.toString(Charsets.UTF_8))
            val matches = Regex("(?m)^%RUN-RECOVERY-KEY-V1 (RUN-BK1-[A-F0-9-]+)\\r?$").findAll(bytes.toString(Charsets.ISO_8859_1)).take(2).toList()
            require(matches.size == 1) { "This PDF has no intact embedded recovery key. Import the key file or enter the printed key." }
            return BackupRecoveryKey.parse(matches.single().groupValues[1])
        } finally { bytes.fill(0) }
    }
    internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        try { while (true) {
            val read = read(buffer); if (read < 0) break
            if (read == 0) {
                val next = read(); if (next < 0) break
                require(out.size() < limit) { "Recovery document is too large." }; out.write(next); continue
            }
            require(out.size() + read <= limit) { "Recovery document is too large." }; out.write(buffer, 0, read)
        }; return out.toByteArray() }
        finally { buffer.fill(0) }
    }
}
