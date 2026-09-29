/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.focusbyrj.app.data.AppRepository
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.FocusDatabaseMigrationHelper
import com.focusbyrj.app.data.TaskRepository
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import com.focusbyrj.app.util.backup.AutoBackupScheduler
import net.sqlcipher.database.SupportFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusApplication : Application(), ImageLoaderFactory {
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(com.focusbyrj.app.util.crypto.EncryptedMediaFetcher.Factory())
            }
            .respectCacheHeaders(false)
            .build()
    }
    
    override fun onCreate() {
        super.onCreate()
        com.focusbyrj.app.util.diagnostics.AppLogger.init(this)
        com.focusbyrj.app.util.diagnostics.CrashHandler.init(this)
        try {
            net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        } catch (t: Throwable) {
            android.util.Log.e("FocusApplication", "Failed to load SQLCipher libs early", t)
        }
        com.focusbyrj.app.util.AppThemeManager.init(this)
        com.focusbyrj.app.util.LicenseManager.init(this)
        com.focusbyrj.app.util.FocusStatsManager.init(this)
        com.focusbyrj.app.util.FocusEconomyManager.init(this)
        com.focusbyrj.app.util.AptitudeManager.init(this)
        com.focusbyrj.app.util.DailyQuestManager.init(this)
        com.focusbyrj.app.util.CustomCategoryManager.init(this)
        com.focusbyrj.app.util.BubbleChatManager.init(this)
        com.focusbyrj.app.util.AppIconManager.init(this)
        com.focusbyrj.app.util.StreakManager.init(this)
        com.focusbyrj.app.data.drill.DrillSessionRepository.init(this)
        com.focusbyrj.app.service.HabitReceiver.createHabitNotificationChannel(this)
        com.focusbyrj.app.util.AyvaAlertCategory.createNotificationChannels(this)

        try {
            app.rive.runtime.kotlin.core.Rive.init(this)
        } catch (e: Throwable) {
            try {
                app.rive.runtime.kotlin.core.Rive.init(this, app.rive.runtime.kotlin.core.RendererType.Canvas)
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }

        // Warm up Ayva knowledge base, start intelligent AutoSyncManager, and
        // schedule daily rolling auto-backup (7-day retention, internal storage).
        val ioScope = CoroutineScope(Dispatchers.IO)
        ioScope.launch {
            com.focusbyrj.app.util.AyvaTalkEngine.warmUp(this@FocusApplication)
            com.focusbyrj.app.util.sync.supabase.AutoSyncManager.init(this@FocusApplication)
            try {
                habitRepository.cleanPlaceholderData(this@FocusApplication)
            } catch (t: Throwable) {
                android.util.Log.e("FocusApplication", "Failed to clean placeholder habit data", t)
            }
        }
        AutoBackupScheduler.schedule(this, ioScope)
    }

    val database by lazy { 
        val instance = try {
            val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(this)
            val factory = SupportFactory(passphrase, null, false)
            Room.databaseBuilder(
                this,
                FocusDatabase::class.java,
                FocusDatabaseMigrationHelper.getEncryptedDatabaseName()
            )
            .openHelperFactory(factory)
            .addMigrations(
                FocusDatabase.MIGRATION_1_2,
                FocusDatabase.MIGRATION_2_3,
                FocusDatabase.MIGRATION_3_4,
                FocusDatabase.MIGRATION_1_4,
                FocusDatabase.MIGRATION_2_4,
                FocusDatabase.MIGRATION_4_5,
                FocusDatabase.MIGRATION_1_5,
                FocusDatabase.MIGRATION_5_6,
                FocusDatabase.MIGRATION_1_6,
                FocusDatabase.MIGRATION_6_7,
                FocusDatabase.MIGRATION_1_7,
                FocusDatabase.MIGRATION_7_8,
                FocusDatabase.MIGRATION_1_8,
                FocusDatabase.MIGRATION_8_9,
                FocusDatabase.MIGRATION_7_9,
                FocusDatabase.MIGRATION_1_9,
                FocusDatabase.MIGRATION_9_10,
                FocusDatabase.MIGRATION_8_10,
                FocusDatabase.MIGRATION_7_10,
                FocusDatabase.MIGRATION_1_10,
                FocusDatabase.MIGRATION_10_11,
                FocusDatabase.MIGRATION_9_11,
                FocusDatabase.MIGRATION_8_11,
                FocusDatabase.MIGRATION_7_11,
                FocusDatabase.MIGRATION_1_11,
                FocusDatabase.MIGRATION_11_12,
                FocusDatabase.MIGRATION_1_12
            )
            .build()
        } catch (t: Throwable) {
            val isRobolectric = try {
                android.os.Build.FINGERPRINT.startsWith("robolectric") ||
                Class.forName("org.robolectric.Robolectric") != null
            } catch (_: Throwable) {
                false
            }
            if (isRobolectric) {
                android.util.Log.w("FocusApplication", "Robolectric test environment detected; utilizing in-memory test database", t)
                Room.inMemoryDatabaseBuilder(this, FocusDatabase::class.java)
                    .fallbackToDestructiveMigration()
                    .build()
            } else {
                android.util.Log.e("FocusApplication", "FATAL: Failed to securely initialize SQLCipher FocusDatabase", t)
                throw SecurityException("Database encryption failure: unable to securely initialize SQLCipher FocusDatabase. Fail-closed security enforced.", t)
            }
        }

        // Delete any insecure legacy fallback database if one existed
        try {
            val fallbackDb = getDatabasePath("focus_database_fallback.db")
            if (fallbackDb.exists()) {
                fallbackDb.delete()
            }
        } catch (_: Exception) {}

        try {
            FocusDatabaseMigrationHelper.checkAndMigrateIfLegacyPlaintextExists(this, instance)
        } catch (t: Throwable) {
            android.util.Log.e("FocusApplication", "FocusDatabase legacy migration failed gracefully", t)
        }

        instance
    }
    
    val vocabDatabase by lazy {
        Room.databaseBuilder(
            this,
            com.focusbyrj.app.data.VocabDatabase::class.java,
            "vocab.db"
        )
        .createFromAsset("vocab.db")
        .addMigrations(com.focusbyrj.app.data.VocabDatabase.MIGRATION_1_2)
        .build()
    }
    
    val repository by lazy { AppRepository(database.appRestrictionDao(), database.scheduleDao()) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val habitRepository by lazy { com.focusbyrj.app.data.HabitRepository(database.habitDao()) }
    val vocabRepository by lazy { com.focusbyrj.app.data.VocabRepository(vocabDatabase.vocabDao()) }
}
