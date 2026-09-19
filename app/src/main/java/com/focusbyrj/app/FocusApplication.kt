/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app

import android.app.Application
import androidx.room.Room
import com.focusbyrj.app.data.AppRepository
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        try {
            net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        } catch (t: Throwable) {
            android.util.Log.e("FocusApplication", "Failed to load SQLCipher libs early", t)
        }
        com.focusbyrj.app.util.AppThemeManager.init(this)
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

        // Warm up Ayva knowledge base in background IO thread for instant 0ms responses
        CoroutineScope(Dispatchers.IO).launch {
            com.focusbyrj.app.util.AyvaTalkEngine.warmUp(this@FocusApplication)
        }
    }

    val database by lazy { 
        Room.databaseBuilder(
            this,
            FocusDatabase::class.java,
            "focus_database"
        )
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
            FocusDatabase.MIGRATION_1_9
        )
        .fallbackToDestructiveMigration()
        .build() 
    }
    
    val vocabDatabase by lazy {
        Room.databaseBuilder(
            this,
            com.focusbyrj.app.data.VocabDatabase::class.java,
            "vocab.db"
        )
        .createFromAsset("vocab.db")
        .addMigrations(com.focusbyrj.app.data.VocabDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
    }
    
    val repository by lazy { AppRepository(database.appRestrictionDao(), database.scheduleDao()) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val habitRepository by lazy { com.focusbyrj.app.data.HabitRepository(database.habitDao()) }
    val vocabRepository by lazy { com.focusbyrj.app.data.VocabRepository(vocabDatabase.vocabDao()) }
}
