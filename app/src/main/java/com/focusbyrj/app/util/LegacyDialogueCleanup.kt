package com.focusbyrj.app.util

import android.content.Context
import java.io.File
import java.nio.file.Files

/** Retired public engine data only; chat history and authored user data are kept. */
internal object LegacyDialogueCleanup {
    fun remove(context: Context) {
        listOf(File(context.noBackupFilesDir, "general-native-assets"),
            File(context.cacheDir, "general-native-turn")).forEach { root ->
            runCatching { delete(root) }
        }
    }
    private fun delete(file: File) {
        if (Files.isSymbolicLink(file.toPath())) { file.delete(); return }
        if (file.isDirectory) file.listFiles()?.forEach(::delete)
        file.delete()
    }
}
