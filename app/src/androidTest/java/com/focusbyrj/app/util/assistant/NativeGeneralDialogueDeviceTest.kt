package com.focusbyrj.app.util.assistant

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Installed-APK check: extraction, executable permissions, protocol and private scratch cleanup. */
@RunWith(AndroidJUnit4::class)
class NativeGeneralDialogueDeviceTest {
    @Test fun compareNativeAndKotlinOnTheSameBoundedPhrases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val samples = listOf("Could you possibly recommend something relaxing?", "I feel exhausted",
            "I prefer quiet evenings", "I am worried about studying", "why that", "what could I try",
            "can zebras juggle purple cucumbers", "I am not worried about studying")
        val style = GeneralDialogue.Style(true, false, false)
        val frame = GeneralDialogue.answer("I prefer quiet evenings", null, style)!!.frame
        val rows = org.json.JSONArray()
        val beforePss = android.os.Debug.getPss()
        samples.forEach { input ->
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            val kotlin = GeneralDialogue.answer(input, frame, style)
            val kotlinUs = (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1000
            val nativeStart = android.os.SystemClock.elapsedRealtimeNanos()
            val meaning = NativeGeneralDialogue.interpret(context, input)
            val nativeUs = (android.os.SystemClock.elapsedRealtimeNanos() - nativeStart) / 1000
            rows.put(org.json.JSONObject().put("sample", input).put("kotlinRule", kotlin?.rule ?: "none")
                .put("nativeMeaning", meaning ?: "none").put("kotlinUs", kotlinUs).put("nativeUs", nativeUs))
        }
        val report = org.json.JSONObject().put("samples", rows).put("appPssBeforeKb", beforePss)
            .put("appPssAfterKb", android.os.Debug.getPss()).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("abi", android.os.Build.SUPPORTED_ABIS.first()).put("memoryScope", "app PSS only; child peak not measured")
        File(context.cacheDir, "focused-native-comparison.json").writeText(report.toString(2))
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", report.toString(2)) })
        assertFalse(File(context.cacheDir, "general-native-turn").exists())
    }
    @Test fun workerRunsFromInstalledLibraryDirectoryWithoutKeepingInput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("what could I try", NativeGeneralDialogue.interpret(context, "Could you possibly recommend something relaxing?"))
        assertEquals("I feel tired", NativeGeneralDialogue.interpret(context, "I feel exhausted"))
        assertNull(NativeGeneralDialogue.interpret(context, "can zebras juggle purple cucumbers"))
        assertNull(NativeGeneralDialogue.interpret(context, "I am not worried about studying"))
        assertFalse(File(context.cacheDir, "general-native-turn").exists())
        val assets = File(context.noBackupFilesDir, "general-native-assets")
        assertTrue(assets.walkTopDown().any { it.name == ".ready" })
        assertFalse(assets.walkTopDown().any { it.isFile && it.name.startsWith("topic_run-transient") })
    }
}
