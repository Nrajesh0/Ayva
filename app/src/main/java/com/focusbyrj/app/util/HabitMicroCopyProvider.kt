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

package com.focusbyrj.app.util

import android.content.Context
import com.focusbyrj.app.data.Habit
import java.util.Calendar
import kotlin.random.Random

/**
 * Provides natural, grounded, authentic micro-copy quotes and streak flame formatting
 * customized to the specific habit, time of day, progress, and streak level.
 *
 * Employs a non-repeating LRU history cache so the user never sees the same quote
 * in short intervals.
 */
object HabitMicroCopyProvider {

    private const val PREFS_NAME = "habit_microcopy_prefs"
    private const val KEY_RECENT_QUOTES = "recent_quote_keys"
    private const val MAX_RECENT_HISTORY = 65

    enum class Category {
        HYDRATION,
        POSTURE_MOVEMENT,
        MINDFULNESS_BREATH,
        READING_LEARNING,
        FITNESS_WORKOUT,
        GENERAL_HABIT
    }

    enum class TimeOfDay {
        MORNING,    // 05:00 - 11:59
        AFTERNOON,  // 12:00 - 17:59
        EVENING     // 18:00 - 04:59
    }

    enum class ProgressState {
        FIRST_LOG,      // completed == 0
        IN_PROGRESS,    // 0 < completed < target - 1
        ONE_TO_GO,      // completed == target - 1 (target > 1)
        ALL_DONE        // completed >= target
    }

    data class Quote(
        val id: String,
        val text: String,
        val category: Category? = null,
        val preferredTime: TimeOfDay? = null,
        val preferredProgress: ProgressState? = null,
        val minStreak: Int? = null
    )

    data class StreakBadgeInfo(
        val label: String,
        val isBurning: Boolean,
        val days: Int,
        val flameEmoji: String = "🩵",
        val primaryColorHex: String = "#38BDF8",
        val bgAlphaInt: Int = 55
    )

    fun getStreakBadge(streakDays: Int, habit: Habit? = null): StreakBadgeInfo {
        val category = habit?.let { getCategory(it) } ?: Category.GENERAL_HABIT

        val flameEmoji = when (category) {
            Category.HYDRATION -> "💧"
            Category.POSTURE_MOVEMENT -> "⚡"
            Category.MINDFULNESS_BREATH -> "🩵"
            Category.READING_LEARNING -> "📚"
            Category.FITNESS_WORKOUT -> "💪"
            Category.GENERAL_HABIT -> if (streakDays >= 7) "💙" else "🩵"
        }

        val (colorHex, bgAlpha) = when (category) {
            Category.HYDRATION -> Pair("#38BDF8", 55)         // Sky Blue
            Category.POSTURE_MOVEMENT -> Pair("#60A5FA", 50)  // Bright Vibrant Blue
            Category.MINDFULNESS_BREATH -> Pair("#818CF8", 55)// Indigo Lavender Blue
            Category.READING_LEARNING -> Pair("#2DD4BF", 50)  // Cyan Teal
            Category.FITNESS_WORKOUT -> Pair("#3B82F6", 55)   // Sapphire Blue
            Category.GENERAL_HABIT -> if (streakDays >= 7) Pair("#00F0FF", 60) else Pair("#38BDF8", 50)
        }

        val label = when {
            streakDays <= 0 -> "🌱 Day 1"
            streakDays == 1 -> "$flameEmoji 1d Streak"
            streakDays in 2..6 -> "$flameEmoji ${streakDays}d Streak"
            else -> "$flameEmoji ${streakDays}d On Fire"
        }

        return StreakBadgeInfo(
            label = label,
            isBurning = streakDays >= 2,
            days = streakDays,
            flameEmoji = flameEmoji,
            primaryColorHex = colorHex,
            bgAlphaInt = bgAlpha
        )
    }

    fun getCategory(habit: Habit): Category {
        val title = habit.title.lowercase()
        val desc = habit.description.lowercase()
        val emoji = habit.iconEmoji

        return when {
            emoji in listOf("🥤", "💧", "🚰", "🥛", "☕", "🍵") ||
            listOf("water", "drink", "hydrat", "sip", "fluid", "glass", "bottle", "cup").any { title.contains(it) || desc.contains(it) } ->
                Category.HYDRATION

            emoji in listOf("🧍", "🚶", "🏃", "🤸", "🦴", "🧘‍♂️", "🚶‍♂️") ||
            listOf("stretch", "posture", "stand", "walk", "back", "neck", "spine", "desk").any { title.contains(it) || desc.contains(it) } ->
                Category.POSTURE_MOVEMENT

            emoji in listOf("🧘", "✨", "🧠", "🕯️", "🌬️", "🌸") ||
            listOf("meditat", "breath", "mindful", "calm", "zen", "pause", "reflect", "journal", "relax", "gratitude").any { title.contains(it) || desc.contains(it) } ->
                Category.MINDFULNESS_BREATH

            emoji in listOf("📚", "📖", "✍️", "📝", "💡") ||
            listOf("read", "book", "study", "learn", "write", "pages", "chapter", "code", "notes").any { title.contains(it) || desc.contains(it) } ->
                Category.READING_LEARNING

            emoji in listOf("💪", "🏋️", "🥊", "🚴", "🏃‍♂️") ||
            listOf("workout", "gym", "pushup", "run", "squat", "cardio", "sweat", "exercise", "lift", "training").any { title.contains(it) || desc.contains(it) } ->
                Category.FITNESS_WORKOUT

            else -> Category.GENERAL_HABIT
        }
    }

    private fun getCurrentTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.AFTERNOON
            else -> TimeOfDay.EVENING
        }
    }

    private fun getProgressState(completed: Int, target: Int): ProgressState {
        return when {
            completed <= 0 -> ProgressState.FIRST_LOG
            target > 1 && completed >= target -> ProgressState.ALL_DONE
            target > 1 && completed == target - 1 -> ProgressState.ONE_TO_GO
            else -> ProgressState.IN_PROGRESS
        }
    }

    /**
     * Resolves a natural, conversational quote without repeating recent selections.
     */
    fun getNaturalQuote(
        context: Context,
        habit: Habit,
        completedCount: Int,
        targetCount: Int,
        streakDays: Int
    ): String {
        val category = getCategory(habit)
        val timeOfDay = getCurrentTimeOfDay()
        val progress = getProgressState(completedCount, targetCount)

        // Read recent history
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recentRaw = prefs.getString(KEY_RECENT_QUOTES, "") ?: ""
        val recentList = recentRaw.split(",").filter { it.isNotBlank() }.toMutableList()

        // 1. Gather all pool candidates for this category + high-priority progress/time matches
        val candidates = QUOTE_LIBRARY.filter { q ->
            val catMatch = q.category == null || q.category == category
            val timeMatch = q.preferredTime == null || q.preferredTime == timeOfDay
            val progMatch = q.preferredProgress == null || q.preferredProgress == progress
            val streakMatch = q.minStreak == null || streakDays >= q.minStreak
            catMatch && (timeMatch || progMatch) && streakMatch
        }.ifEmpty {
            QUOTE_LIBRARY.filter { (it.category == null || it.category == category) && (it.minStreak == null || streakDays >= it.minStreak) }
        }.ifEmpty {
            QUOTE_LIBRARY
        }

        // 2. Filter out recently seen quotes to prevent repetition
        var freshCandidates = candidates.filter { it.id !in recentList }

        // If exhausted, clear the older half of history
        if (freshCandidates.isEmpty()) {
            val half = recentList.size / 2
            val trimmedRecent = recentList.drop(half)
            recentList.clear()
            recentList.addAll(trimmedRecent)
            freshCandidates = candidates.filter { it.id !in recentList }
        }

        val chosen = (freshCandidates.ifEmpty { candidates }).randomOrNull()
            ?: Quote("fallback", "One small win. Check it off and keep going.")

        // 3. Update history
        recentList.add(chosen.id)
        while (recentList.size > MAX_RECENT_HISTORY) {
            recentList.removeAt(0)
        }
        prefs.edit().putString(KEY_RECENT_QUOTES, recentList.joinToString(",")).apply()

        // Interpolate streak if template placeholder exists
        val rawText = chosen.text
        return if (rawText.contains("{streak}")) {
            rawText.replace("{streak}", streakDays.coerceAtLeast(1).toString())
        } else {
            rawText
        }
    }

    // =========================================================================
    // EXTENSIVE LIBRARY OF NATURAL, SIMPLE-WORDED CONTEXTUAL QUOTES (220+ entries)
    // =========================================================================
    private val QUOTE_LIBRARY = listOf(
        // ---------------------------------------------------------------------
        // HYDRATION: MORNING (Waking up, kickstarting hydration, pre-caffeine)
        // ---------------------------------------------------------------------
        Quote("w_m_1", "Cold water first thing. Wakes the whole brain up.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_2", "Hydrate before you caffeinate.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_3", "Start the day with a full glass. Easy early win.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_4", "Your body went 8 hours with zero water. Drink up.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_5", "One quick cup now sets the tone for today.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_6", "A big glass to wash away the morning grogginess.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_7", "Clean slate, fresh glass of water.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_8", "Drink early so you're not playing catch-up all day.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_9", "Kickstart your morning metabolism with cold water.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_10", "Glass in hand. Best way to start the morning.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_11", "Pour a tall glass before touching your coffee.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_12", "First victory of the day: one tall glass of water.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_13", "Clear water on an empty stomach feels amazing.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_14", "Start your morning crisp and well-hydrated.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_15", "Rehydrate after sleep. Your brain will thank you.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_16", "The cleanest morning routine: water, breathe, conquer.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_17", "Morning hydration puts your mind straight into gear.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),
        Quote("w_m_18", "One full glass down. Morning momentum unlocked.", Category.HYDRATION, preferredTime = TimeOfDay.MORNING),

        // ---------------------------------------------------------------------
        // HYDRATION: AFTERNOON (Slump, screen fatigue, focus recharge)
        // ---------------------------------------------------------------------
        Quote("w_a_1", "Afternoon fog? A cold glass fixes 80% of it.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_2", "Take 10 seconds. Cold water break.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_3", "Beat the 2 PM crash with a fresh cup.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_4", "Screen tired your eyes out? Drink some water.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_5", "Grab a refill and reset your focus.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_6", "A quick sip keeps your energy steady.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_7", "Dry throat check. Go grab a refill.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_8", "Step away to get water. Instant recharge.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_9", "Hydration is the cheapest focus booster.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_10", "Don't ignore the dry mouth. Sip now.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_11", "One cold drink to reset the rest of your workday.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_12", "Refill the bottle while you stretch your legs.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_13", "Feeling drained? You might just need water, not coffee.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_14", "A fresh cup of water is a 20-second mental reset.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_15", "Keep the bottle next to your keyboard.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_16", "Midday checkpoint: how much water have you had?", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_17", "Swap that soda for a cold, crisp glass of water.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_18", "Halfway through the workday. Keep sipping.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_19", "Drink before you feel parched. Stay ahead of the slump.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_20", "Stand up, walk to the tap, take a cold drink.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),
        Quote("w_a_21", "Hydrate between tasks. Keep your head clear.", Category.HYDRATION, preferredTime = TimeOfDay.AFTERNOON),

        // ---------------------------------------------------------------------
        // HYDRATION: EVENING (Winding down, dinner balance, restful sleep)
        // ---------------------------------------------------------------------
        Quote("w_e_1", "One glass before dinner keeps things balanced.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_2", "Wrap up your hydration before winding down.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_3", "A sip now so you rest easy tonight.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_4", "Finish off the last bit of your daily water.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_5", "Hydrated body, much deeper sleep tonight.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_6", "A gentle sip before shutting down your day.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_7", "End the day hydrated. Tomorrow will thank you.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_8", "Keep a fresh glass by your nightstand.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_9", "Clear out the day's fatigue with a peaceful glass of water.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_10", "Wind down with water, not screen time.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_11", "One last sip to round out your daily target.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),
        Quote("w_e_12", "Your evening reset: dim the lights, sip some water.", Category.HYDRATION, preferredTime = TimeOfDay.EVENING),

        // ---------------------------------------------------------------------
        // HYDRATION: PROGRESS BASED (First log, in progress, one to go, all done)
        // ---------------------------------------------------------------------
        Quote("w_p_1", "First sip of the day. Get the ball rolling.", Category.HYDRATION, preferredProgress = ProgressState.FIRST_LOG),
        Quote("w_p_2", "Glass number one checked. Strong start.", Category.HYDRATION, preferredProgress = ProgressState.FIRST_LOG),
        Quote("w_p_3", "Water bottle is halfway empty. Good pace.", Category.HYDRATION, preferredProgress = ProgressState.IN_PROGRESS),
        Quote("w_p_4", "Halfway through your daily water target. Keep sipping.", Category.HYDRATION, preferredProgress = ProgressState.IN_PROGRESS),
        Quote("w_p_5", "More than halfway there! Keep the bottle filled.", Category.HYDRATION, preferredProgress = ProgressState.IN_PROGRESS),
        Quote("w_p_6", "Just one more glass to hit your target today!", Category.HYDRATION, preferredProgress = ProgressState.ONE_TO_GO),
        Quote("w_p_7", "Final glass of the day. Knock it out.", Category.HYDRATION, preferredProgress = ProgressState.ONE_TO_GO),
        Quote("w_p_8", "Last glass standing between you and 100% hydration.", Category.HYDRATION, preferredProgress = ProgressState.ONE_TO_GO),
        Quote("w_p_9", "Goal reached today! Feel free to drink extra.", Category.HYDRATION, preferredProgress = ProgressState.ALL_DONE),
        Quote("w_p_10", "Daily water target crushed. Pure clarity.", Category.HYDRATION, preferredProgress = ProgressState.ALL_DONE),
        Quote("w_p_11", "Target complete! Your body is completely fueled.", Category.HYDRATION, preferredProgress = ProgressState.ALL_DONE),
        Quote("w_p_12", "All daily water logged. You're a hydration machine.", Category.HYDRATION, preferredProgress = ProgressState.ALL_DONE),

        // ---------------------------------------------------------------------
        // HYDRATION: EVERGREEN, WITTY & NATURAL (Real talk)
        // ---------------------------------------------------------------------
        Quote("w_g_1", "One quick glass and you're good.", Category.HYDRATION),
        Quote("w_g_2", "Nothing fancy. Just pure water.", Category.HYDRATION),
        Quote("w_g_3", "Don't wait until you feel parched.", Category.HYDRATION),
        Quote("w_g_4", "Your future self will thank you for this sip.", Category.HYDRATION),
        Quote("w_g_5", "Fill it up. Take a breath. Drink.", Category.HYDRATION),
        Quote("w_g_6", "Hydrated brain, faster work.", Category.HYDRATION),
        Quote("w_g_7", "Quick swallow and right back to work.", Category.HYDRATION),
        Quote("w_g_8", "Sip by sip, that's how it's done.", Category.HYDRATION),
        Quote("w_g_9", "Water is the original energy drink.", Category.HYDRATION),
        Quote("w_g_10", "Two big gulps right now. Feel that?", Category.HYDRATION),
        Quote("w_g_11", "Water in, brain fog out.", Category.HYDRATION),
        Quote("w_g_12", "Clear water, clear head.", Category.HYDRATION),
        Quote("w_g_13", "Keep your water bottle within arm's reach.", Category.HYDRATION),
        Quote("w_g_14", "Nobody ever regretted a fresh glass of water.", Category.HYDRATION),
        Quote("w_g_15", "Small regular sips beat gulping all at once.", Category.HYDRATION),
        Quote("w_g_16", "Just ten seconds to drink a cup. Easy win.", Category.HYDRATION),
        Quote("w_g_17", "Your body is 60% water. Don't run on empty.", Category.HYDRATION),
        Quote("w_g_18", "Water fixes more problems than you think.", Category.HYDRATION),
        Quote("w_g_19", "Headache creeping in? Drink a tall glass first.", Category.HYDRATION),
        Quote("w_g_20", "Clear skin, sharp focus, all starts with water.", Category.HYDRATION),
        Quote("w_g_21", "Drink some water. You're basically a houseplant with complex emotions.", Category.HYDRATION),
        Quote("w_g_22", "Cold, pure, zero calories, 100% vital.", Category.HYDRATION),
        Quote("w_g_23", "A cold glass of water is nature's restart button.", Category.HYDRATION),
        Quote("w_g_24", "If you're reading this, take a sip right now.", Category.HYDRATION),
        Quote("w_g_25", "Hydration is the ultimate zero-effort health hack.", Category.HYDRATION),
        Quote("w_g_26", "Take a deep breath and take three big gulps.", Category.HYDRATION),
        Quote("w_g_27", "That water bottle isn't going to drink itself.", Category.HYDRATION),
        Quote("w_g_28", "Your brain is mostly water. Feed it what it needs.", Category.HYDRATION),
        Quote("w_g_29", "Drink water now, thank yourself later.", Category.HYDRATION),
        Quote("w_g_30", "Pure water. The easiest self-care there is.", Category.HYDRATION),
        Quote("w_g_31", "Keep sipping. Consistency is everything.", Category.HYDRATION),
        Quote("w_g_32", "A glass of water is a mini-vacation for your brain.", Category.HYDRATION),
        Quote("w_g_33", "Water: the silent productivity multiplier.", Category.HYDRATION),
        Quote("w_g_34", "Don't overthink it. Just drink a glass.", Category.HYDRATION),
        Quote("w_g_35", "Sweat out, water in. Rehydrate those cells.", Category.HYDRATION),
        Quote("w_g_36", "Cold water on a warm day is unmatched.", Category.HYDRATION),
        Quote("w_g_37", "Replenish and recharge. Drink up.", Category.HYDRATION),
        Quote("w_g_38", "Two cups closer to optimal hydration.", Category.HYDRATION),
        Quote("w_g_39", "Sip water like it's your job.", Category.HYDRATION),
        Quote("w_g_40", "Stay fueled, stay clear, stay hydrated.", Category.HYDRATION),

        // ---------------------------------------------------------------------
        // POSTURE & MOVEMENT: DESK RESET, SHOULDERS, SPINE, EYES
        // ---------------------------------------------------------------------
        Quote("s_1", "Drop your shoulders. Unclench your jaw.", Category.POSTURE_MOVEMENT),
        Quote("s_2", "Roll your neck back for five seconds.", Category.POSTURE_MOVEMENT),
        Quote("s_3", "Stand up and shake the chair stiffness off.", Category.POSTURE_MOVEMENT),
        Quote("s_4", "You've been in that chair a while. Stretch!", Category.POSTURE_MOVEMENT, preferredTime = TimeOfDay.AFTERNOON),
        Quote("s_5", "Stand tall for 30 seconds. Feels good.", Category.POSTURE_MOVEMENT),
        Quote("s_6", "Quick spine stretch, big deep breath.", Category.POSTURE_MOVEMENT),
        Quote("s_7", "Uncross your legs and straighten up.", Category.POSTURE_MOVEMENT),
        Quote("s_8", "Roll your shoulders back. Better, right?", Category.POSTURE_MOVEMENT),
        Quote("s_9", "Step away from the screen for sixty seconds.", Category.POSTURE_MOVEMENT),
        Quote("s_10", "A fast walk across the room resets your mood.", Category.POSTURE_MOVEMENT),
        Quote("s_11", "Shake out your wrists and fingers.", Category.POSTURE_MOVEMENT),
        Quote("s_12", "Your back and neck will thank you later.", Category.POSTURE_MOVEMENT),
        Quote("s_13", "Motion changes emotion every time.", Category.POSTURE_MOVEMENT),
        Quote("s_14", "Break the desk freeze. Just move a little.", Category.POSTURE_MOVEMENT),
        Quote("s_15", "Morning stretch to kick off your body.", Category.POSTURE_MOVEMENT, preferredTime = TimeOfDay.MORNING),
        Quote("s_16", "Release whatever tension built up today.", Category.POSTURE_MOVEMENT, preferredTime = TimeOfDay.EVENING),
        Quote("s_17", "Look 20 feet away for 20 seconds. Let your eyes relax.", Category.POSTURE_MOVEMENT),
        Quote("s_18", "Interlace your fingers and push to the ceiling.", Category.POSTURE_MOVEMENT),
        Quote("s_19", "Tuck your chin slightly. Protect your neck.", Category.POSTURE_MOVEMENT),
        Quote("s_20", "Open up your chest. Take up a little space.", Category.POSTURE_MOVEMENT),
        Quote("s_21", "Gently twist side to side. Free that spine.", Category.POSTURE_MOVEMENT),
        Quote("s_22", "Don't let the desk swallow you whole today.", Category.POSTURE_MOVEMENT),
        Quote("s_23", "Get blood moving into your toes and calves.", Category.POSTURE_MOVEMENT),
        Quote("s_24", "A 30-second stand beats hours of hunching.", Category.POSTURE_MOVEMENT),
        Quote("s_25", "Arch your back gently backwards. Instant relief.", Category.POSTURE_MOVEMENT),
        Quote("s_26", "Wrists get tight from typing. Circle them out.", Category.POSTURE_MOVEMENT),
        Quote("s_27", "Your posture quietly tells your brain how to feel.", Category.POSTURE_MOVEMENT),
        Quote("s_28", "Shake off the stiffness before the next task.", Category.POSTURE_MOVEMENT),
        Quote("s_29", "Stand up, reach for your toes, exhale.", Category.POSTURE_MOVEMENT),
        Quote("s_30", "Straighten your spine. Breathe into your belly.", Category.POSTURE_MOVEMENT),
        Quote("s_31", "Ten second neck rolls. Feel the reset?", Category.POSTURE_MOVEMENT),
        Quote("s_32", "Take a walking lap around the room.", Category.POSTURE_MOVEMENT),
        Quote("s_33", "Eyes off glass, look out a window if you can.", Category.POSTURE_MOVEMENT),
        Quote("s_34", "Sit all the way back into your chair.", Category.POSTURE_MOVEMENT),
        Quote("s_35", "Tension builds up quietly. Shake it off.", Category.POSTURE_MOVEMENT),
        Quote("s_36", "Loosen your forehead, relax your temples.", Category.POSTURE_MOVEMENT),
        Quote("s_37", "A short stand-and-stretch does wonders.", Category.POSTURE_MOVEMENT),
        Quote("s_38", "Squeeze your shoulder blades together for 5 seconds.", Category.POSTURE_MOVEMENT),

        // ---------------------------------------------------------------------
        // MINDFULNESS & BREATH: CALM, GROUNDING, PAUSE
        // ---------------------------------------------------------------------
        Quote("m_1", "Deep breath in through your nose... and out.", Category.MINDFULNESS_BREATH),
        Quote("m_2", "Pause the noise for thirty seconds.", Category.MINDFULNESS_BREATH),
        Quote("m_3", "Close your eyes. Count to four. Exhale.", Category.MINDFULNESS_BREATH),
        Quote("m_4", "Clear out the open tabs in your head.", Category.MINDFULNESS_BREATH),
        Quote("m_5", "You don't need to rush this minute.", Category.MINDFULNESS_BREATH),
        Quote("m_6", "Just be here right now. One slow breath.", Category.MINDFULNESS_BREATH),
        Quote("m_7", "Soften your forehead and take a breath.", Category.MINDFULNESS_BREATH),
        Quote("m_8", "Small pause, much clearer thoughts.", Category.MINDFULNESS_BREATH),
        Quote("m_9", "Ground your feet on the floor. Inhale.", Category.MINDFULNESS_BREATH),
        Quote("m_10", "Let the hurry go for just one minute.", Category.MINDFULNESS_BREATH),
        Quote("m_11", "Reset your mind before jumping to what's next.", Category.MINDFULNESS_BREATH),
        Quote("m_12", "Sixty seconds of quiet does wonders.", Category.MINDFULNESS_BREATH),
        Quote("m_13", "Peaceful start to a clear morning.", Category.MINDFULNESS_BREATH, preferredTime = TimeOfDay.MORNING),
        Quote("m_14", "Let go of whatever happened earlier today.", Category.MINDFULNESS_BREATH, preferredTime = TimeOfDay.EVENING),
        Quote("m_15", "Feel the weight of your feet against the floor.", Category.MINDFULNESS_BREATH),
        Quote("m_16", "Breathe in calm, breathe out the rush.", Category.MINDFULNESS_BREATH),
        Quote("m_17", "One deep, slow breath right down to your belly.", Category.MINDFULNESS_BREATH),
        Quote("m_18", "The urgent can wait. Take this quiet moment.", Category.MINDFULNESS_BREATH),
        Quote("m_19", "Release the mental grip for just a second.", Category.MINDFULNESS_BREATH),
        Quote("m_20", "Nothing else to solve right at this second.", Category.MINDFULNESS_BREATH),
        Quote("m_21", "Quiet the noise. Check in with how you feel.", Category.MINDFULNESS_BREATH),
        Quote("m_22", "Notice what's around you without judging it.", Category.MINDFULNESS_BREATH),
        Quote("m_23", "Take a slow four-count inhale... hold... release.", Category.MINDFULNESS_BREATH),
        Quote("m_24", "You're doing plenty today. Give yourself grace.", Category.MINDFULNESS_BREATH),
        Quote("m_25", "A moment of stillness makes the next hour easier.", Category.MINDFULNESS_BREATH),
        Quote("m_26", "Drop the mental baggage. Exhale completely.", Category.MINDFULNESS_BREATH),
        Quote("m_27", "Even one mindful breath shifts your nervous system.", Category.MINDFULNESS_BREATH),
        Quote("m_28", "Calm mind, steady hands, sharp focus.", Category.MINDFULNESS_BREATH),
        Quote("m_29", "Let your thoughts pass like clouds. Just breathe.", Category.MINDFULNESS_BREATH),
        Quote("m_30", "Be present for 30 seconds. That's mindfulness.", Category.MINDFULNESS_BREATH),
        Quote("m_31", "Close your eyes and listen to the room.", Category.MINDFULNESS_BREATH),
        Quote("m_32", "A quiet head is your greatest superpower.", Category.MINDFULNESS_BREATH),
        Quote("m_33", "Step off the mental treadmill for a second.", Category.MINDFULNESS_BREATH),
        Quote("m_34", "Breathe in energy, exhale the overwhelm.", Category.MINDFULNESS_BREATH),
        Quote("m_35", "Evening stillness. Let today be enough.", Category.MINDFULNESS_BREATH, preferredTime = TimeOfDay.EVENING),
        Quote("m_36", "Morning calm sets the tone for everything else.", Category.MINDFULNESS_BREATH, preferredTime = TimeOfDay.MORNING),
        Quote("m_37", "No rushing. No urgency. Just this breath.", Category.MINDFULNESS_BREATH),
        Quote("m_38", "Two slow deep breaths to center yourself.", Category.MINDFULNESS_BREATH),

        // ---------------------------------------------------------------------
        // READING & LEARNING: DIRECT, INVITING, LOW FRICTION
        // ---------------------------------------------------------------------
        Quote("r_1", "Even two pages count.", Category.READING_LEARNING),
        Quote("r_2", "Feed your curious mind for five minutes.", Category.READING_LEARNING),
        Quote("r_3", "Put the phone face down, open the page.", Category.READING_LEARNING),
        Quote("r_4", "One chapter, pure quiet focus.", Category.READING_LEARNING),
        Quote("r_5", "Slow down and soak in a few thoughts.", Category.READING_LEARNING),
        Quote("r_6", "A few minutes of reading settles the day.", Category.READING_LEARNING, preferredTime = TimeOfDay.EVENING),
        Quote("r_7", "No notifications here, just words.", Category.READING_LEARNING),
        Quote("r_8", "Small reading habits build huge knowledge.", Category.READING_LEARNING),
        Quote("r_9", "Just ten quiet minutes.", Category.READING_LEARNING),
        Quote("r_10", "A fresh idea is waiting on the next page.", Category.READING_LEARNING),
        Quote("r_11", "One page today is better than twenty pages never.", Category.READING_LEARNING),
        Quote("r_12", "Feed your brain something high quality.", Category.READING_LEARNING),
        Quote("r_13", "Turn the page. What happens next?", Category.READING_LEARNING),
        Quote("r_14", "Five minutes with a real book recalibrates attention.", Category.READING_LEARNING),
        Quote("r_15", "Read to learn, read to pause, read to explore.", Category.READING_LEARNING),
        Quote("r_16", "Words that outlive the timeline.", Category.READING_LEARNING),
        Quote("r_17", "Trade ten minutes of scrolling for ten pages of reading.", Category.READING_LEARNING),
        Quote("r_18", "Deep reading is an antidote to short attention spans.", Category.READING_LEARNING),
        Quote("r_19", "Crack the spine, dive in for a few minutes.", Category.READING_LEARNING),
        Quote("r_20", "Curiosity is a muscle. Give it a quick workout.", Category.READING_LEARNING),
        Quote("r_21", "One insight today can change your whole month.", Category.READING_LEARNING),
        Quote("r_22", "Just finish the section you're on.", Category.READING_LEARNING),
        Quote("r_23", "Morning pages spark sharp thoughts.", Category.READING_LEARNING, preferredTime = TimeOfDay.MORNING),
        Quote("r_24", "Evening reading lets the day dissolve peacefully.", Category.READING_LEARNING, preferredTime = TimeOfDay.EVENING),
        Quote("r_25", "Every book is a conversation with a great mind.", Category.READING_LEARNING),
        Quote("r_26", "Keep the bookmark moving forward.", Category.READING_LEARNING),
        Quote("r_27", "Ten pages a day is thirty books a year.", Category.READING_LEARNING),
        Quote("r_28", "Sink into the text without checking the clock.", Category.READING_LEARNING),
        Quote("r_29", "Quiet brain food. Pick it up.", Category.READING_LEARNING),
        Quote("r_30", "Learn one new thing today. Even a small one.", Category.READING_LEARNING),
        Quote("r_31", "A book is quiet company.", Category.READING_LEARNING),
        Quote("r_32", "Read a paragraph. Think about it for a second.", Category.READING_LEARNING),

        // ---------------------------------------------------------------------
        // FITNESS & WORKOUT: PUNCHY, REALISTIC, ACTION FIRST
        // ---------------------------------------------------------------------
        Quote("f_1", "Five pushups right now. Done is done.", Category.FITNESS_WORKOUT),
        Quote("f_2", "Action first, motivation follows.", Category.FITNESS_WORKOUT),
        Quote("f_3", "You never regret moving your body.", Category.FITNESS_WORKOUT),
        Quote("f_4", "Lace up and get the blood flowing.", Category.FITNESS_WORKOUT),
        Quote("f_5", "Ten squats. Quickest energy boost.", Category.FITNESS_WORKOUT),
        Quote("f_6", "Small reps compound into real strength.", Category.FITNESS_WORKOUT),
        Quote("f_7", "Show up for 5 minutes. That's the secret.", Category.FITNESS_WORKOUT),
        Quote("f_8", "A little sweat, a lot of clear thinking.", Category.FITNESS_WORKOUT),
        Quote("f_9", "Get your body moving before the day takes over.", Category.FITNESS_WORKOUT, preferredTime = TimeOfDay.MORNING),
        Quote("f_10", "Finish the day strong. Move.", Category.FITNESS_WORKOUT, preferredTime = TimeOfDay.EVENING),
        Quote("f_11", "Even 10 jumping jacks spikes alertness.", Category.FITNESS_WORKOUT),
        Quote("f_12", "Do the reps. Your future self is smiling.", Category.FITNESS_WORKOUT),
        Quote("f_13", "Don't negotiate with comfort. Just start.", Category.FITNESS_WORKOUT),
        Quote("f_14", "Your muscles want to work. Let them.", Category.FITNESS_WORKOUT),
        Quote("f_15", "One set right now. Takes 45 seconds.", Category.FITNESS_WORKOUT),
        Quote("f_16", "Physical energy creates mental energy.", Category.FITNESS_WORKOUT),
        Quote("f_17", "Wake up the heart rate for a quick minute.", Category.FITNESS_WORKOUT),
        Quote("f_18", "Strength is earned one rep at a time.", Category.FITNESS_WORKOUT),
        Quote("f_19", "Put your shoes on. The rest takes care of itself.", Category.FITNESS_WORKOUT),
        Quote("f_20", "A little burn feels strangely good.", Category.FITNESS_WORKOUT),
        Quote("f_21", "Movement is medicine. Dose yourself.", Category.FITNESS_WORKOUT),
        Quote("f_22", "Ten bodyweight squats while you wait.", Category.FITNESS_WORKOUT),
        Quote("f_23", "Nothing beats the feeling after a good workout.", Category.FITNESS_WORKOUT),
        Quote("f_24", "Make your body proud of you today.", Category.FITNESS_WORKOUT),
        Quote("f_25", "Consistency beats intensity every single time.", Category.FITNESS_WORKOUT),
        Quote("f_26", "Quick burst of movement clears the slate.", Category.FITNESS_WORKOUT),
        Quote("f_27", "Get the blood pumping through your legs.", Category.FITNESS_WORKOUT),
        Quote("f_28", "You're only one workout away from a better mood.", Category.FITNESS_WORKOUT),
        Quote("f_29", "Do the hard thing first. Feel great all day.", Category.FITNESS_WORKOUT, preferredTime = TimeOfDay.MORNING),
        Quote("f_30", "Even a 15-minute workout counts.", Category.FITNESS_WORKOUT),
        Quote("f_31", "Build momentum with one clean set.", Category.FITNESS_WORKOUT),

        // ---------------------------------------------------------------------
        // GENERAL HABIT & STREAK: REAL TALK, MOMENTUM, GROUNDED
        // ---------------------------------------------------------------------
        Quote("g_1", "Day by day. That's the whole game.", Category.GENERAL_HABIT),
        Quote("g_2", "One small step. Check it off.", Category.GENERAL_HABIT),
        Quote("g_3", "Small choices win the whole week.", Category.GENERAL_HABIT),
        Quote("g_4", "You showed up today. That counts.", Category.GENERAL_HABIT),
        Quote("g_5", "Show up for yourself, even for a minute.", Category.GENERAL_HABIT),
        Quote("g_6", "Keep the chain unbroken.", Category.GENERAL_HABIT),
        Quote("g_7", "Consistency over perfection, always.", Category.GENERAL_HABIT),
        Quote("g_8", "Just tick this box and keep rolling.", Category.GENERAL_HABIT),
        Quote("g_9", "Small wins add up faster than you think.", Category.GENERAL_HABIT),
        Quote("g_10", "You're building something that lasts.", Category.GENERAL_HABIT),
        Quote("g_11", "No need to be perfect, just present.", Category.GENERAL_HABIT),
        Quote("g_12", "One tiny checkmark for today.", Category.GENERAL_HABIT),
        Quote("g_13", "Morning ritual locked in.", Category.GENERAL_HABIT, preferredTime = TimeOfDay.MORNING),
        Quote("g_14", "Afternoon reboot. You got this.", Category.GENERAL_HABIT, preferredTime = TimeOfDay.AFTERNOON),
        Quote("g_15", "Evening wrap-up. Rest easy tonight.", Category.GENERAL_HABIT, preferredTime = TimeOfDay.EVENING),
        Quote("g_16", "You're halfway there today. Keep it up.", Category.GENERAL_HABIT, preferredProgress = ProgressState.IN_PROGRESS),
        Quote("g_17", "Almost at your goal! Just one more.", Category.GENERAL_HABIT, preferredProgress = ProgressState.ONE_TO_GO),
        Quote("g_18", "Last one for today. Finish it strong!", Category.GENERAL_HABIT, preferredProgress = ProgressState.ONE_TO_GO),
        Quote("g_19", "All checked off today! Nice job.", Category.GENERAL_HABIT, preferredProgress = ProgressState.ALL_DONE),
        Quote("g_20", "That's a solid streak. Protect the flame!", Category.GENERAL_HABIT, minStreak = 2),
        Quote("g_21", "Look at that streak! Keep it burning.", Category.GENERAL_HABIT, minStreak = 3),
        Quote("g_22", "Day one is where every streak starts.", Category.GENERAL_HABIT, preferredProgress = ProgressState.FIRST_LOG),
        Quote("g_23", "Two minutes now saves twenty minutes of regret.", Category.GENERAL_HABIT),
        Quote("g_24", "Do it now, then you don't have to think about it.", Category.GENERAL_HABIT),
        Quote("g_25", "One less thing on your mental plate.", Category.GENERAL_HABIT),
        Quote("g_26", "The secret of getting ahead is getting started.", Category.GENERAL_HABIT),
        Quote("g_27", "Take the easy win right now.", Category.GENERAL_HABIT),
        Quote("g_28", "Check it off, release the mental burden.", Category.GENERAL_HABIT),
        Quote("g_29", "Habits are the compound interest of self-improvement.", Category.GENERAL_HABIT),
        Quote("g_30", "Don't break the chain. You've come too far.", Category.GENERAL_HABIT, minStreak = 3),
        Quote("g_31", "A streak isn't luck. It's showing up.", Category.GENERAL_HABIT, minStreak = 2),
        Quote("g_32", "Momentum is hard to build, easy to keep.", Category.GENERAL_HABIT, minStreak = 2),
        Quote("g_33", "One tap now, peace of mind all evening.", Category.GENERAL_HABIT),
        Quote("g_34", "Progress isn't loud. It looks like this.", Category.GENERAL_HABIT),
        Quote("g_35", "Quiet discipline beats loud motivation.", Category.GENERAL_HABIT),
        Quote("g_36", "You said you'd do it today. Deliver to yourself.", Category.GENERAL_HABIT),
        Quote("g_37", "Keep the promise you made to yourself this morning.", Category.GENERAL_HABIT),
        Quote("g_38", "Small actions today, massive results in six months.", Category.GENERAL_HABIT),
        Quote("g_39", "Lock in this win before moving to the next thing.", Category.GENERAL_HABIT),
        Quote("g_40", "You're on day {streak}. Don't let today be day zero.", Category.GENERAL_HABIT, minStreak = 2),
        Quote("g_41", "Day {streak} streak locked in. Keep the fire burning.", Category.GENERAL_HABIT, minStreak = 2),
        Quote("g_42", "Just get this one done. The rest can wait.", Category.GENERAL_HABIT),
        Quote("g_43", "A minute of effort, hours of feeling good about it.", Category.GENERAL_HABIT),
        Quote("g_44", "You're in the flow. Keep going.", Category.GENERAL_HABIT),
        Quote("g_45", "Every time you check this, you reinforce who you are.", Category.GENERAL_HABIT),
        Quote("g_46", "Today's effort protects yesterday's streak.", Category.GENERAL_HABIT, minStreak = 1),
        Quote("g_47", "Done is a wonderful feeling. Tap it.", Category.GENERAL_HABIT),
        Quote("g_48", "Win the morning, win the day.", Category.GENERAL_HABIT, preferredTime = TimeOfDay.MORNING),
        Quote("g_49", "Afternoon checkpoint. Steady as she goes.", Category.GENERAL_HABIT, preferredTime = TimeOfDay.AFTERNOON),
        Quote("g_50", "Nightcap victory. Rest proud tonight.", Category.GENERAL_HABIT, preferredTime = TimeOfDay.EVENING),
        Quote("g_51", "Showing up when you don't feel like it is what matters.", Category.GENERAL_HABIT),
        Quote("g_52", "Tick this off. Your future self will nod in approval.", Category.GENERAL_HABIT),
        Quote("g_53", "Routine simplifies life. Check.", Category.GENERAL_HABIT),
        Quote("g_54", "A 10-second win to keep the ball rolling.", Category.GENERAL_HABIT),
        Quote("g_55", "Step by step, day by day, streak by streak.", Category.GENERAL_HABIT)
    )
}
