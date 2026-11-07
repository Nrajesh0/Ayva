package com.focusbyrj.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.util.backup.MaintenanceOpenHelperFactory
import com.focusbyrj.app.util.crypto.TestRuntimeKeys
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

internal object SecureDatabaseNames {
    const val NOTES = "keep_notes_vault.db"
    const val FOCUS = "focus_database_vault.db"
}

/** All device databases fail closed. Framework SQLite is used only by JVM tests. */
internal object SecureRoomFactory {
    fun create(context: Context): SupportSQLiteOpenHelper.Factory {
        if (TestRuntimeKeys.available) return MaintenanceOpenHelperFactory()
        SQLiteDatabase.loadLibs(context.applicationContext)
        return MaintenanceOpenHelperFactory(SupportFactory(DatabaseKeyProvider.getOrCreatePassphrase(context), null, false))
    }
}
