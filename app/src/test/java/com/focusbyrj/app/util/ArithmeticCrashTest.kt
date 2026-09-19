package com.focusbyrj.app.util

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArithmeticCrashTest {
    @Test
    fun testGenerate() {
        for (i in 0 until 1000) {
            val qEasy = ArithmeticEngine.generateQuestion(ArithmeticDifficulty.EASY)
            assertNotNull(qEasy.title)
            assertNotNull(qEasy.questionText)
            assertTrue(qEasy.options.isNotEmpty())
            assertTrue(qEasy.correctIndex in qEasy.options.indices)

            val qMed = ArithmeticEngine.generateQuestion(ArithmeticDifficulty.MEDIUM)
            assertNotNull(qMed.title)
            assertNotNull(qMed.questionText)
            assertTrue(qMed.options.isNotEmpty())
            assertTrue(qMed.correctIndex in qMed.options.indices)

            val qHard = ArithmeticEngine.generateQuestion(ArithmeticDifficulty.HARD)
            assertNotNull(qHard.title)
            assertNotNull(qHard.questionText)
            assertTrue(qHard.options.isNotEmpty())
            assertTrue(qHard.correctIndex in qHard.options.indices)
        }
    }

    @Test
    fun testInequalitiesQuestionConsistency() {
        // Specifically test 500 inequality questions across difficulties
        for (difficulty in listOf(ArithmeticDifficulty.EASY, ArithmeticDifficulty.MEDIUM, ArithmeticDifficulty.HARD)) {
            for (i in 0 until 200) {
                val q = ArithmeticEngine.generateQuestion(difficulty)
                if (q.title == "Reasoning: Inequalities") {
                    assertEquals(5, q.options.size)
                    assertTrue(q.correctIndex in 0..4)

                    val lines = q.questionText.lines()
                    val c1Line = lines.firstOrNull { it.startsWith("I. ") }
                    val c2Line = lines.firstOrNull { it.startsWith("II. ") }
                    assertNotNull("Conclusion I must exist", c1Line)
                    assertNotNull("Conclusion II must exist", c2Line)

                    // Verify explanation references the exact conclusions
                    val c1Relation = c1Line!!.removePrefix("I. ").trim()
                    val c2Relation = c2Line!!.removePrefix("II. ").trim()
                    assertTrue("Explanation must reference Conclusion I ($c1Relation)", q.explanation.contains(c1Relation))
                    assertTrue("Explanation must reference Conclusion II ($c2Relation)", q.explanation.contains(c2Relation))
                }
            }
        }
    }

    @Test
    fun testLottieAssets() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val assets = listOf(
            "cat_morning.lottie", "cat_evening.lottie", "cat_error.lottie",
            "cat_angry.lottie", "cat_action.lottie", "cat_dance.lottie", "cat_dancing.lottie"
        ) + com.focusbyrj.app.service.HabitFloatingOverlayManager.HABIT_LOTTIE_ASSETS
        for (asset in assets) {
            val res = com.airbnb.lottie.LottieCompositionFactory.fromAssetSync(context, asset)
            println("Asset: $asset, exception: ${res.exception}")
            assertNull("Failed to load $asset: ${res.exception}", res.exception)
            assertNotNull("Composition was null for $asset", res.value)
        }
    }
}
