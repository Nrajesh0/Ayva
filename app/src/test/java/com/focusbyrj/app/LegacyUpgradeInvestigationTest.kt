package com.focusbyrj.app

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.data.VocabDatabase
import com.focusbyrj.app.data.note.DatabaseKeyProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Actual historical Room schemas; the framework driver tests migration SQL, not SQLCipher JNI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class)
class LegacyUpgradeInvestigationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun historicalSchemasUpgradeWithoutLosingTasksHabitsOrBoundaries() {
        // Schema12 is the unchanged exported schema from release 2.0.0 (fe8da10).
        for (version in 12..15) {
            val path = "schemas/com.focusbyrj.app.data.FocusDatabase/$version.json"
            val fixture = listOf(File(path), File("app/$path")).first { it.exists() }
            val schema = JSONObject(fixture.readText()).getJSONObject("database")
            val name = "historical-upgrade-$version.db"
            context.deleteDatabase(name)
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val entities = schema.getJSONArray("entities")
                            for (i in 0 until entities.length()) {
                                val entity = entities.getJSONObject(i)
                                val table = entity.getString("tableName")
                                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                                entity.optJSONArray("indices")?.let { indices ->
                                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j)
                                        .getString("createSql").replace("\${TABLE_NAME}", table))
                                }
                                if (table in setOf("tasks", "habits", "habit_logs", "app_restrictions", "focus_schedules")) {
                                    val fields = entity.getJSONArray("fields")
                                    val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
                                    val values = (0 until fields.length()).map { j ->
                                        val field = fields.getJSONObject(j)
                                        when (field.getString("columnName")) {
                                            "id", "habitId" -> 73L
                                            "title", "name", "appName" -> "Kept from old release"
                                            "packageName" -> "com.example.legacy"
                                            "details" -> "Preserved task details"
                                            "description" -> "Preserved habit details"
                                            "type" -> if (table == "tasks") "TASK" else "ONCE_DAILY"
                                            "recurrence" -> "NONE"
                                            "mode" -> "HARD"
                                            "restrictionMode" -> "SIMPLE"
                                            "subtasksJson", "pauseHistoryJson" -> "[]"
                                            "date" -> "2026-11-01"
                                            "targetPerDay", "targetCount", "completedCount", "isRestricted", "isEnabled" -> 1L
                                            else -> if (!field.getBoolean("notNull")) null
                                                else if (field.getString("affinity") == "TEXT") "" else 0L
                                        }
                                    }.toTypedArray()
                                    db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})", values)
                                }
                            }
                            val setup = schema.getJSONArray("setupQueries")
                            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture must not upgrade")
                    }).build())
            helper.writableDatabase
            helper.close()
            repeat(2) {
                val room = Room.databaseBuilder(context, FocusDatabase::class.java, name)
                    .addMigrations(FocusDatabase.MIGRATION_12_13, FocusDatabase.MIGRATION_13_14,
                        FocusDatabase.MIGRATION_14_15, FocusDatabase.MIGRATION_15_16)
                    .allowMainThreadQueries().build()
                try {
                    val db = room.openHelper.writableDatabase // Executes migration and Room schema validation.
                    assertEquals(16, db.version)
                    for (table in listOf("tasks", "habits", "habit_logs", "app_restrictions", "focus_schedules")) {
                        db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); assertEquals("$version $table", 1, c.getInt(0)) }
                    }
                    db.query("SELECT title,details,planningJson FROM tasks WHERE id=73").use { c ->
                        assertTrue(c.moveToFirst()); assertEquals("Kept from old release", c.getString(0))
                        assertEquals("Preserved task details", c.getString(1)); assertEquals("", c.getString(2))
                    }
                    db.query("SELECT description,pausedAt,pauseHistoryJson,frequencyJson FROM habits WHERE id=73").use { c ->
                        assertTrue(c.moveToFirst()); assertEquals("Preserved habit details", c.getString(0))
                        assertTrue(c.isNull(1)); assertEquals("[]", c.getString(2)); assertEquals("", c.getString(3))
                    }
                } finally { room.close() }
            }
            context.deleteDatabase(name)
        }
    }

    @Test fun oldSoftwareWrappedDatabaseKeyRemainsReadableWithoutReplacingIt() {
        // Exact legacy seed and preference names used in 2.0.0; test data only.
        val seed = MessageDigest.getInstance("SHA-256").digest("focus_notes_sqlcipher_software_seed_v1".toByteArray())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(seed, "AES"))
        val passphrase = ByteArray(32) { (it + 1).toByte() }
        val encrypted = Base64.encodeToString(cipher.doFinal(passphrase), Base64.NO_WRAP)
        val prefs = context.getSharedPreferences("focus_notes_vault_security_prefs", 0)
        prefs.edit().clear().putString("enc_db_passphrase", encrypted)
            .putString("enc_db_passphrase_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit()
        DatabaseKeyProvider.clearCachedPassphrase()
        try {
            assertArrayEquals(passphrase, DatabaseKeyProvider.getOrCreatePassphrase(context))
            assertEquals(encrypted, prefs.getString("enc_db_passphrase", null))
        } finally { DatabaseKeyProvider.clearCachedPassphrase() }
    }

    @Test fun existingVersionTwoVocabularyKeepsLearnedAndBookmarkedEntries() {
        // This bundled version1 asset is byte-identical in the published 2.0.0 and 2.0.39 APKs.
        val name = "legacy-vocabulary-upgrade.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        context.assets.open("vocab.db").use { input -> file.outputStream().use { input.copyTo(it) } }
        val old = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) = error("Expected the historical asset")
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    assertEquals(1, oldVersion); VocabDatabase.MIGRATION_1_2.migrate(db)
                }
            }).build())
        val previous = old.writableDatabase
        val counts = listOf("idioms", "ows").associateWith { table ->
            previous.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) }
        }
        previous.execSQL("UPDATE idioms SET is_mastered=1,is_bookmarked=1,learned_at=123456 WHERE id=(SELECT MIN(id) FROM idioms)")
        old.close()
        val room = Room.databaseBuilder(context, VocabDatabase::class.java, name)
            .addMigrations(VocabDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(3, db.version)
            counts.forEach { (table, count) -> db.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); assertEquals(count, c.getInt(0)) } }
            db.query("SELECT is_mastered,is_bookmarked,learned_at FROM idioms ORDER BY id LIMIT 1").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)); assertEquals(1, c.getInt(1)); assertEquals(123456L, c.getLong(2))
            }
            db.query("SELECT COUNT(*) FROM dictionary_bookmarks").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        } finally { room.close(); context.deleteDatabase(name) }
    }

    @Test fun legacyActiveFocusFlagGetsADeadlineWithoutCrashingOrCancellingProtection() {
        val prefs = context.getSharedPreferences("focus_prefs", 0)
        prefs.edit().clear().putBoolean("isSessionActive", true).commit()
        com.focusbyrj.app.util.FocusStatsManager.init(context)
        com.focusbyrj.app.util.FocusEconomyManager.init(context)
        val manager = com.focusbyrj.app.util.FocusSessionManager
        try {
            manager.restore(context)
            assertTrue(manager.active.value)
            assertTrue(prefs.getLong("focus_end_wall_ms", 0) > System.currentTimeMillis())
            assertEquals(1500L, prefs.getLong("focus_duration_sec", 0))
        } finally { manager.stop(context) }
    }
}
