package com.focusbyrj.app.util.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteTransactionListener
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** Gate the actual SQLite mutation boundary, rather than a subset of UI callers. */
internal class MaintenanceOpenHelperFactory(
    private val factory: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val delegate = configuration.callback
        val preserving = object : SupportSQLiteOpenHelper.Callback(delegate.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                // AndroidX's default callback closes and deletes database files.
                // A damaged/unreadable user store must remain available for recovery.
                throw SecurityException("Database integrity check failed. Existing files have been retained for recovery.")
            }
        }
        val safeConfiguration = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name).callback(preserving)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(false).build()
        val helper = factory.create(safeConfiguration)
        return object : SupportSQLiteOpenHelper by helper {
            override val writableDatabase: SupportSQLiteDatabase get() = GatedDatabase(helper.writableDatabase)
            override val readableDatabase: SupportSQLiteDatabase get() = GatedDatabase(helper.readableDatabase)
        }
    }
}
private class GatedDatabase(private val db: SupportSQLiteDatabase) : SupportSQLiteDatabase by db {
    private fun begin(block: () -> Unit) {
        MaintenanceGate.enter()
        try { block() } catch (error: Throwable) { MaintenanceGate.exit(); throw error }
    }
    override fun beginTransaction() = begin { db.beginTransaction() }
    override fun beginTransactionNonExclusive() = begin { db.beginTransactionNonExclusive() }
    override fun beginTransactionWithListener(listener: SQLiteTransactionListener) = begin { db.beginTransactionWithListener(listener) }
    override fun beginTransactionWithListenerNonExclusive(listener: SQLiteTransactionListener) = begin { db.beginTransactionWithListenerNonExclusive(listener) }
    override fun endTransaction() { try { db.endTransaction() } finally { MaintenanceGate.exit() } }
    override fun execSQL(sql: String) = MaintenanceGate.write { db.execSQL(sql) }
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) = MaintenanceGate.write { db.execSQL(sql, bindArgs) }
    override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long = MaintenanceGate.write { db.insert(table, conflictAlgorithm, values) }
    override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int = MaintenanceGate.write { db.update(table, conflictAlgorithm, values, whereClause, whereArgs) }
    override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = MaintenanceGate.write { db.delete(table, whereClause, whereArgs) }
    override fun compileStatement(sql: String): SupportSQLiteStatement {
        val statement = db.compileStatement(sql)
        return object : SupportSQLiteStatement by statement {
            override fun execute() = MaintenanceGate.write { statement.execute() }
            override fun executeInsert(): Long = MaintenanceGate.write { statement.executeInsert() }
            override fun executeUpdateDelete(): Int = MaintenanceGate.write { statement.executeUpdateDelete() }
        }
    }
}
