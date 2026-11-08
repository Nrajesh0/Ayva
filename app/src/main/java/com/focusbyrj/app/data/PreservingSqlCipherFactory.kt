package com.focusbyrj.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File
import net.sqlcipher.DatabaseErrorHandler
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

/**
 * SQLCipher 4.5.4's SupportFactory installs a destructive native error handler;
 * it does not forward corruption to the AndroidX callback. Supply the handler
 * at the native boundary so failed reads cannot delete the recovery material.
 */
internal class PreservingSqlCipherFactory(private val passphrase: ByteArray) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        SQLiteDatabase.loadLibs(configuration.context.applicationContext)
        val callback = configuration.callback
        val name = configuration.name
        val path = if (name != null && configuration.useNoBackupDirectory)
            File(configuration.context.noBackupFilesDir, name).absolutePath else name
        val helper = object : SQLiteOpenHelper(
            configuration.context.applicationContext, path, null, callback.version, null,
            DatabaseErrorHandler {
                // Do not delegate to SQLCipher's deleting default handler or retry
                // by creating a fresh database. Keep every existing byte for recovery.
                throw SecurityException("Database integrity check failed. Existing files have been retained for recovery.")
            }
        ) {
            override fun onConfigure(db: SQLiteDatabase) = callback.onConfigure(db)
            override fun onCreate(db: SQLiteDatabase) = callback.onCreate(db)
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SQLiteDatabase) = callback.onOpen(db)
        }
        return object : SupportSQLiteOpenHelper {
            override val databaseName: String? get() = name
            override val writableDatabase: SupportSQLiteDatabase get() = helper.getWritableDatabase(passphrase)
            // Match SupportFactory: Room needs a writable connection for its setup.
            override val readableDatabase: SupportSQLiteDatabase get() = writableDatabase
            override fun setWriteAheadLoggingEnabled(enabled: Boolean) = helper.setWriteAheadLoggingEnabled(enabled)
            // Maintenance can close and reopen the same helper. Retain the same
            // key for that lifetime, as with the prior clearPassphrase=false setup.
            override fun close() = helper.close()
        }
    }
}
