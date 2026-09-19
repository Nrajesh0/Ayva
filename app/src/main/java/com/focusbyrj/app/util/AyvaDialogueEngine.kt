package com.focusbyrj.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.Calendar
import kotlin.random.Random

object AyvaDialogueEngine {
    private const val PREFS_NAME = "ayva_dialogue_prefs"

    // --- LEVENSHTEIN FUZZY MATCHING (No-AI Engine Upgrade) ---
    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    private fun isFuzzyMatch(word: String, target: String, maxDistance: Int = 1): Boolean {
        if (word == target) return true
        if (kotlin.math.abs(word.length - target.length) > maxDistance) return false
        return levenshtein(word, target) <= maxDistance
    }

    private fun containsAnyFuzzy(text: String, keywords: List<String>, maxDistance: Int = 1): Boolean {
        val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
        for (word in words) {
            for (keyword in keywords) {
                if (isFuzzyMatch(word, keyword, maxDistance)) {
                    return true
                }
            }
        }
        return false
    }

    // --- NON-REPEATING SHUFFLE DECK LOGIC ---
    private fun getNextFromDeck(context: Context?, categoryKey: String, pool: List<String>): String {
        if (pool.isEmpty()) return ""
        if (context == null) return pool.first()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedJson = prefs.getString("deck_$categoryKey", null)
        val remainingIndices = mutableListOf<Int>()
        if (storedJson != null) {
            kotlin.runCatching {
                val array = JSONArray(storedJson)
                for (i in 0 until array.length()) {
                    val idx = array.getInt(i)
                    if (idx in pool.indices) {
                        remainingIndices.add(idx)
                    }
                }
            }
        }
        if (remainingIndices.isEmpty()) {
            remainingIndices.addAll(pool.indices)
            remainingIndices.shuffle()
        }
        val chosenIndex = remainingIndices.removeAt(0)
        
        // Save updated deck
        val newArray = JSONArray()
        remainingIndices.forEach { newArray.put(it) }
        prefs.edit().putString("deck_$categoryKey", newArray.toString()).apply()
        
        return pool[chosenIndex]
    }

    // =========================================================================
    // 1. TASK CREATION DIALOGUES (Upgraded with Contextual Moods & Fuzzy NLP)
    // =========================================================================
    fun getTaskAddedResponse(
        context: Context,
        title: String,
        isPriority: Boolean,
        hasDueDate: Boolean,
        dueDateStr: String?,
        attrStr: String?
    ): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val lower = title.lowercase()
        val dueSuffix = if (!dueDateStr.isNullOrBlank()) " for $dueDateStr" else ""
        val attrSuffix = if (!attrStr.isNullOrBlank()) " $attrStr" else ""
        
        // Contextual Mood: Streak-Aware Responses
        val profile = AptitudeManager.profileFlow.value
        val streak = profile.currentStreak
        
        // If the user has a high streak, Ayva gets extremely hyped and acknowledging.
        val moodPrefix = if (streak >= 7) {
            "🔥 Week-long streak energy! "
        } else if (streak >= 3) {
            "⚡ You're on a roll! "
        } else {
            ""
        }

        // Context: Time of Day
        if (hour < 5 || hour >= 22) {
            val lateNightQuips = listOf(
                "Logged: \"$title\"$dueSuffix. It's getting late—whenever you're ready, take some time to rest. 🌙",
                "Saved \"$title\"$dueSuffix. It'll be waiting for you tomorrow. Wishing you a peaceful night. ✨",
                "Noted: \"$title\"$dueSuffix. Be gentle on yourself tonight and wind down when you can. 🌿",
                "Added \"$title\"$dueSuffix to your tasks. You've worked hard today; get some rest soon. 🍵",
                "All recorded: \"$title\"$dueSuffix. Rest well tonight so you wake up refreshed. 🌙"
            )
            return getNextFromDeck(context, "task_late_night", lateNightQuips)
        }
        if (hour in 5..8) {
            val earlyMorningQuips = listOf(
                "${moodPrefix}Up with the sun and already locking in goals? Respect. Added$attrSuffix: \"$title\"$dueSuffix. 🌅",
                "${moodPrefix}Early bird getting the worm! \"$title\"$dueSuffix is on today's hit list.",
                "${moodPrefix}Starting strong before the rest of the world wakes up. Added: \"$title\"$dueSuffix. ⚡",
                "${moodPrefix}Morning energy detected! Logged$attrSuffix: \"$title\"$dueSuffix. Let's make today count.",
                "${moodPrefix}Coffee in hand, goals in sight. Added \"$title\"$dueSuffix. Let's roll! ☕"
            )
            return getNextFromDeck(context, "task_early_morning", earlyMorningQuips)
        }

        // Context: High Priority
        if (isPriority) {
            val priorityQuips = listOf(
                "🚨 Top priority alert! Moved \"$title\"$dueSuffix straight to the VIP lounge of your task list.",
                "Red banner engaged! \"$title\"$dueSuffix is marked high priority. All eyes on this one! 🎯",
                "Marked as critical! \"$title\"$dueSuffix is at the top of my radar. Let's conquer it.",
                "Priority stamped! \"$title\"$dueSuffix is not to be messed around with today. 🔥",
                "Locked in as urgent: \"$title\"$dueSuffix. Don't let this one linger!"
            )
            return getNextFromDeck(context, "task_priority", priorityQuips)
        }

        // Context: Category-based witty reactions with FUZZY MATCHING (Synonyms & Typos)
        val fitnessSynonyms = listOf("gym", "workout", "run", "exercise", "lift", "jog", "sprint", "cardio", "marathon", "fitness", "yoga")
        if (containsAnyFuzzy(lower, fitnessSynonyms)) {
            val fitnessQuips = listOf(
                "${moodPrefix}Gains incoming! Added \"$title\"$dueSuffix. Don't skip leg day! 🏋️",
                "${moodPrefix}Locked in: \"$title\"$dueSuffix. Sweating today means flexing tomorrow. 💪",
                "${moodPrefix}Added \"$title\"$dueSuffix. Hydrate, conquer, repeat! 🏃",
                "${moodPrefix}Fitness item logged: \"$title\"$dueSuffix. Your future self is already feeling stronger."
            )
            return getNextFromDeck(context, "task_fitness", fitnessQuips)
        }

        val studySynonyms = listOf("study", "exam", "assignment", "read", "chapter", "homework", "quiz", "test", "essay", "learn")
        if (containsAnyFuzzy(lower, studySynonyms)) {
            val studyQuips = listOf(
                "${moodPrefix}Brain gains on the schedule! Added \"$title\"$dueSuffix. Focus mode primed! 📚",
                "${moodPrefix}Knowledge is power. Locked in: \"$title\"$dueSuffix. Let's absorb that info! 🧠",
                "${moodPrefix}Academic hustle logged: \"$title\"$dueSuffix. Coffee and deep focus incoming.",
                "${moodPrefix}Added study mission: \"$title\"$dueSuffix. Lock in and conquer the material!"
            )
            return getNextFromDeck(context, "task_study", studyQuips)
        }

        val devSynonyms = listOf("code", "bug", "deploy", "build", "git", "refactor", "programming", "developer", "backend", "frontend", "api")
        if (containsAnyFuzzy(lower, devSynonyms)) {
            val devQuips = listOf(
                "${moodPrefix}Zero bugs allowed! Added \"$title\"$dueSuffix. May your builds be green! 💻",
                "${moodPrefix}Locked in: \"$title\"$dueSuffix. Semicolons placed, compiler pacified. 🚀",
                "${moodPrefix}Dev task registered: \"$title\"$dueSuffix. Time to turn caffeine into clean code.",
                "${moodPrefix}Added: \"$title\"$dueSuffix. Git commit, git push, git productive!"
            )
            return getNextFromDeck(context, "task_coding", devQuips)
        }

        val choreSynonyms = listOf("clean", "laundry", "dish", "dishes", "room", "trash", "grocer", "groceries", "sweep", "mop", "vacuum")
        if (containsAnyFuzzy(lower, choreSynonyms)) {
            val choreQuips = listOf(
                "${moodPrefix}Adulting in progress! Added \"$title\"$dueSuffix. A clean space equals a clean mind. 🧹",
                "${moodPrefix}Domestic victory logged: \"$title\"$dueSuffix. Get it done and enjoy the peace! ✨",
                "${moodPrefix}Added \"$title\"$dueSuffix. Put on some music and knock it out in 10 minutes.",
                "${moodPrefix}Chore locked: \"$title\"$dueSuffix. You'll feel so much lighter once it's checked off."
            )
            return getNextFromDeck(context, "task_chore", choreQuips)
        }

        // Generic Witty Master Pool (Huge variety)
        val masterPool = listOf(
            "${moodPrefix}Locked and loaded! Added$attrSuffix: \"$title\"$dueSuffix. Let's make it happen! 😉",
            "${moodPrefix}On it! Added$attrSuffix: \"$title\"$dueSuffix. No backing out now! 🎯",
            "${moodPrefix}Gotcha! Added$attrSuffix: \"$title\"$dueSuffix. Future you says thanks! ✨",
            "${moodPrefix}Noted and penned into reality: \"$title\"$dueSuffix. You've got this!",
            "${moodPrefix}Consider it on my radar! Added$attrSuffix: \"$title\"$dueSuffix. ⚡",
            "${moodPrefix}Added \"$title\"$dueSuffix to the queue. One small step for you, one giant leap for today's productivity!",
            "${moodPrefix}Logged: \"$title\"$dueSuffix. Clean execution is the name of the game today. 🚀",
            "${moodPrefix}Done deal! \"$title\"$dueSuffix is officially in play.",
            "${moodPrefix}Added! \"$title\"$dueSuffix is scheduled. Ready whenever you are.",
            "${moodPrefix}Boom! \"$title\"$dueSuffix is safely recorded. Now the fun part: doing it! 🔥",
            "${moodPrefix}Stamped and stored: \"$title\"$dueSuffix. Let's check this off in style later.",
            "${moodPrefix}Your wish is my command. Added$attrSuffix: \"$title\"$dueSuffix. 📋",
            "${moodPrefix}Registered: \"$title\"$dueSuffix. I'll make sure you don't forget this one.",
            "${moodPrefix}In the books! Added \"$title\"$dueSuffix. Keep this awesome momentum rolling.",
            "${moodPrefix}Locked in! Added \"$title\"$dueSuffix. Distractions don't stand a chance.",
            "${moodPrefix}Aye aye! \"$title\"$dueSuffix has entered the arena. ⚔️",
            "${moodPrefix}Secured: \"$title\"$dueSuffix. Another puzzle piece added to today's roadmap.",
            "${moodPrefix}Added! \"$title\"$dueSuffix is lined up. Ready to crush it?",
            "${moodPrefix}Pen to paper, bits to bytes: \"$title\"$dueSuffix is live! ✨",
            "${moodPrefix}Logged$attrSuffix: \"$title\"$dueSuffix. Let's turn intentions into achievements!"
        )
        return getNextFromDeck(context, "task_master_pool", masterPool)
    }

    // =========================================================================
    // 2. RESCHEDULING & POSTPONING DIALOGUES
    // =========================================================================
    fun getRescheduleSuccessResponse(context: Context, taskTitle: String, newDueFormatted: String): String {
        val reschedulePool = listOf(
            "Shifted '$taskTitle' to $newDueFormatted. It'll be ready for you then. ✨",
            "Moved '$taskTitle' to $newDueFormatted. Take a breath and reset. 🌿",
            "Pushed '$taskTitle' to $newDueFormatted. Fresh timing set.",
            "Rescheduled '$taskTitle' to $newDueFormatted. One less thing on your mind right now. ⏳",
            "Adjusted! '$taskTitle' is now set for $newDueFormatted. Breathe, recharge, and take it step by step."
        )
        return getNextFromDeck(context, "reschedule_success", reschedulePool)
    }

    fun getPostponeAllResponse(context: Context, count: Int): String {
        val postponePool = listOf(
            "Shifted $count tasks to tomorrow. Giving you a clean slate for tonight. 🌙",
            "Moved $count tasks forward. Reset, rest, and tackle them refreshed tomorrow morning. 🌅",
            "All $count items moved to tomorrow's list. Time to give your mind some well-deserved rest. ✨",
            "Shifted $count tasks to tomorrow. Enjoy the peace of mind tonight. 🍵"
        )
        return getNextFromDeck(context, "postpone_all", postponePool)
    }

    fun getRescheduleHelpResponse(): String {
        val helpVariants = listOf(
            "Usage: /reschedule <number> <time/date>\n_Example: `/reschedule 1 4pm` or `/reschedule 2 tomorrow morning`_",
            "Need to bump a task? Format is: `/reschedule <task#>` `<time>`\n_Example: `/reschedule 1 tomorrow at 10am`_",
            "Usage: `/reschedule <number> <when>`\n_Example: `/reschedule 1 5pm` or `/reschedule 3 next monday`_"
        )
        return helpVariants.random()
    }

    // =========================================================================
    // 3. BRIEFINGS & SUMMARY HEADERS / PROMPTS
    // =========================================================================
    fun getSummaryGreeting(context: Context, isAll: Boolean, hour: Int, dayOfWeek: Int): String {
        if (isAll) {
            val allBriefings = listOf(
                "📋 *__Ayva's Master Briefing__*\n_Here's the full bird's-eye view of everything on deck:_\n",
                "📋 *__The Grand Ledger with Ayva__*\n_Everything pending across all horizons:_\n",
                "📋 *__Master Task Radar__*\n_All open loops in your universe right now:_\n"
            )
            return getNextFromDeck(context, "briefing_all", allBriefings)
        }

        if (hour < 12) {
            val morningBriefings = listOf(
                "🌅 *__Morning Check-In with Ayva__*\n_Rise and shine! Here's today's playbook:_\n",
                "☀️ *__Good Morning, Champion!__*\n_Coffee brewed, goals aligned. Here's what we've got today:_\n",
                "🌅 *__Ayva's Morning Kickstart__*\n_New day, new wins. Here's what's on today's agenda:_\n",
                "☀️ *__Morning Briefing with Ayva__*\n_Let's take a look at what we're tackling today:_\n"
            )
            return getNextFromDeck(context, "briefing_morning", morningBriefings)
        }

        if (hour < 17) {
            val midDayBriefings = listOf(
                "☀️ *__Midday Check-In with Ayva__*\n_Halfway through the day! Here's the current pulse:_\n",
                "⚡ *__Ayva's Afternoon Reality Check__*\n_Quick glance at where our momentum stands:_\n",
                "☀️ *__Midday Status Report__*\n_Here's how today is shaping up so far:_\n",
                "🌤️ *__Afternoon Progress Check with Ayva__*\n_Stay focused! Here's what's still waiting for you:_\n"
            )
            return getNextFromDeck(context, "briefing_afternoon", midDayBriefings)
        }

        val eveningBriefings = listOf(
            "🌙 *__Evening Check-In with Ayva__*\n_Here is where things stand for today:_\n",
            "🌙 *__Nightly Wrap-Up__*\n_A quick look at today's tasks so you can close out the evening smoothly:_\n",
            "✨ *__Evening Reflection__*\n_Here's what you completed today and what's remaining:_\n",
            "🌙 *__End of Day Overview__*\n_A calm glance at your tasks before heading to rest:_\n"
        )
        return getNextFromDeck(context, "briefing_evening", eveningBriefings)
    }

    fun getReschedulePrompt(context: Context): String {
        val prompts = listOf(
            "_Would you like to move any of these to tomorrow, or leave them as is?_",
            "_Feel free to postpone any of these if you need extra breathing room._",
            "_Any tasks you'd like to reschedule for tomorrow morning?_",
            "_You can easily postpone or adjust due dates if today was already full._"
        )
        return getNextFromDeck(context, "reschedule_prompt", prompts)
    }

    fun getEmptyDayMessage(context: Context): String {
        val emptyPool = listOf(
            "🎉 *Look at you, all clear! No pending tasks in sight.*",
            "✨ *Clean slate! Zero tasks on your plate for today. Enjoy the breathing room!*",
            "🎉 *Inbox zero, task zero! You're officially caught up.*",
            "🚀 *Radar is completely clear! What a glorious sight.*",
            "🌟 *Nothing on today's hit list. Feel free to relax or add a new ambition!*"
        )
        return getNextFromDeck(context, "empty_day", emptyPool)
    }

    // =========================================================================
    // 4. PRIORITY RADAR & FOCUS COMMANDS
    // =========================================================================
    fun getPriorityEmptyResponse(context: Context): String {
        val noPriorityPool = listOf(
            "No high-priority fires to put out right now! 🔥 (Queue is clear)",
            "All quiet on the urgent front. No high-priority tasks lurking! ✨",
            "Zero emergencies! Your priority queue is completely clean.",
            "No critical tasks right now. Smooth sailing! ⛵"
        )
        return getNextFromDeck(context, "priority_empty", noPriorityPool)
    }

    fun getLockCommandResponse(context: Context, durationMin: Int): String {
        val lockPool = listOf(
            "🔒 *Focus Lock Engaged for ${durationMin}m!*\n_Distractions are locked in the vault. Go be unstoppable! 🚀_",
            "🛡️ *Shields Up! ${durationMin}m Deep Work Mode Active.*\n_No notifications, no excuses. Time to lock in! ⚡_",
            "🎯 *${durationMin} Minutes of Pure Focus Begins Now!*\n_Tune out the world and build your empire._",
            "🔒 *Ayva's Focus Guard Activated (${durationMin}m).*\n_Put your head down and show them how it's done! ✨_"
        )
        return getNextFromDeck(context, "lock_command", lockPool)
    }

    fun getClearChatIntro(context: Context): String {
        val intros = listOf(
            "💬 *__Ayva__* ✨\n_Your witty focus companion is on deck._\n_Type a task below to lock it in, or use `/` for commands._",
            "💬 *__Ayva's Command Center__* ⚡\n_Clean slate! I'm ready for your tasks, drills, or schedule adjustments._\n_Type away or type `/` for shortcuts._",
            "💬 *__Ayva__* 🎯\n_Fresh chat, clear mind. What are we conquering next?_\n_Add a task or run `/help` anytime._"
        )
        return getNextFromDeck(context, "clear_chat_intro", intros)
    }

    fun getHelloWelcomeMessage(context: Context): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour < 12 -> "Good morning!"
            hour < 17 -> "Good afternoon!"
            hour < 22 -> "Good evening!"
            else -> "Good evening—getting late!"
        }

        if (hour >= 22 || hour < 5) {
            val calmNightWelcome = listOf(
                "🌙 *Hello! Ayva here.* $timeGreeting\n\nTake it easy tonight. If you have any thoughts or tasks you want to jot down so they don't linger in your head, just type them below and rest peacefully.",
                "✨ *Hey there!* $timeGreeting\n\nI'm here if you want to quickly log something for tomorrow or check your schedule, but remember to get good rest tonight. 🍵",
                "🌙 *Ayva on deck.* $timeGreeting\n\nWhether you're closing out your day or planning ahead, I'm ready to keep things organized for you."
            )
            return getNextFromDeck(context, "hello_night", calmNightWelcome)
        }

        val helloPool = listOf(
            "👋 *Hey there! I'm Ayva, your focus & task companion.* ✨\n\n$timeGreeting Drop any task or reminder below to schedule it, or type **/status** to check your focus posture!",
            "✨ *Ayva here!* 🚀\n\n$timeGreeting Tell me what you'd like to accomplish, type **/tasks** to view your list, or type **/advice** for mindful focus tips.",
            "👋 *Hello! I'm Ayva — ready to keep your day on track.* ⚡\n\n$timeGreeting Type any task naturally below (e.g., _\"Review documents at 3pm\"_), or type **/screentime** to review your app usage!",
            "🌟 *Hey! Ayva is on deck.* 🌿\n\n$timeGreeting Let me know what you're working on, or explore **/help** to see everything we can do."
        )
        return getNextFromDeck(context, "hello_welcome", helloPool)
    }

    // =========================================================================
    // 5. APTITUDE & MATH DRILLS
    // =========================================================================
    fun getDrillFastCorrectPraise(context: Context): String {
        val praises = listOf(
            "⚡ Lightning speed! Big brain energy right there.",
            "🎯 Boom! Exact hit, zero hesitation.",
            "🔥 In the zone! Fast and flawless.",
            "🧠 Computational wizardry detected!",
            "✨ Pure precision! You didn't even flinch."
        )
        return getNextFromDeck(context, "drill_praise", praises)
    }

    fun getDrillMissComfort(context: Context): String {
        val comforts = listOf(
            "Oof, close one! Shake it off, next one is yours.",
            "Minor slip! Keep the rhythm going, you've got this.",
            "Almost had it! Quick breath, next question incoming.",
            "No sweat! Even calculators make rounding errors. Onward! 🚀"
        )
        return getNextFromDeck(context, "drill_comfort", comforts)
    }

    fun getDrillSummaryPraise(context: Context, scorePct: Int): String {
        return when {
            scorePct == 100 -> {
                val perfect = listOf(
                    "🏆 *PERFECT SCORE!* Flawless mental agility. You're operating at peak neural frequency! 🧠⚡",
                    "🌟 *100% ACCURACY!* Absolutely unstoppable arithmetic speed. Take a bow! 👑",
                    "🔥 *FLAWLESS DRILL!* 10 out of 10. Your cognitive sharpness is elite today."
                )
                getNextFromDeck(context, "drill_perfect", perfect)
            }
            scorePct >= 80 -> {
                val great = listOf(
                    "⚡ *Fantastic Drill Performance!* Sharp instincts and high focus. Solid mental workout!",
                    "🎯 *Great Job!* You're locked in and calculating with confidence.",
                    "✨ *Strong Session!* Fast reflexes and solid accuracy. Keep that momentum!"
                )
                getNextFromDeck(context, "drill_great", great)
            }
            else -> {
                val good = listOf(
                    "💪 *Workout Complete!* Neural gears have been warmed up. Ready to focus on big tasks!",
                    "🌱 *Solid Effort!* Brain trained and warmed up. Consistency is where the magic happens.",
                    "⚡ *Drill Done!* Mental cobwebs cleared. Let's channel that energy into today's work!"
                )
                getNextFromDeck(context, "drill_good", good)
            }
        }
    }

    fun getMicroDrillPrompt(context: Context): String {
        val pool = listOf(
            "⚡ **Micro-Drill Challenge: Mental Arithmetic**\n\nSharpening your working memory with high-speed calculations triggers dopamine and cuts through procrastination brain fog.\n\n_Answer correctly below to lock in a quick cognitive win!_",
            "🧠 **Neural Warm-Up Activated!**\n\nA 30-second arithmetic sprint engages your prefrontal cortex, priming you for intense, high-friction deep work.\n\n_Calculate swiftly:_",
            "🔥 **Rapid Brain Calisthenics**\n\nClear mental cobwebs and re-center your alertness before your next focus block.\n\n_Solve this drill:_"
        )
        return getNextFromDeck(context, "micro_drill_prompt", pool)
    }

    // =========================================================================
    // 6. CONTEXTUAL FOCUS ADVICE & MINDFUL COACHING
    // =========================================================================
    fun getContextualFocusAdvice(
        context: Context,
        totalScreenTimeMins: Int,
        pendingTasksCount: Int,
        overdueCount: Int,
        habitsCount: Int = 0,
        habitsCompletedCount: Int = 0
    ): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // Late night calm check (after 10 PM or before 5 AM):
        if (hour >= 22 || hour < 5) {
            val nightAdvice = listOf(
                "🌙 *Evening Wind-Down*: It's late and you've likely had a full day. Consider letting your mind unwind—rest tonight is what powers your clarity tomorrow.",
                "🍵 *Rest Suggestion*: If you're feeling depleted, it's completely okay to step back. Give your eyes a break from the screen and get some restful sleep.",
                "✨ *Night Reflection*: You made it through today. Any unfinished tasks can be picked up with a fresh mind in the morning. Rest well."
            )
            return getNextFromDeck(context, "advice_night", nightAdvice)
        }

        return when {
            habitsCount > 0 && habitsCompletedCount == 0 && hour in 9..19 -> {
                val habitPool = listOf(
                    "🌱 *Daily Keystone Habit*: You have $habitsCount daily habit(s) awaiting check-in. Completing even just one micro-habit (like hydration or a 2-min stretch) kickstarts immediate cognitive momentum! ⚡",
                    "✨ *Habit Ignition*: Your habit streak is ready for action today! Checking off one habit now lowers resistance for all subsequent tasks.",
                    "🌊 *Consistency First*: Small daily actions compound into life-changing results. Knock out your easiest habit to build today's focus flow!"
                )
                getNextFromDeck(context, "advice_habits", habitPool)
            }
            overdueCount > 0 -> {
                val overduePool = listOf(
                    "🧹 *Smart Triage Available*: You have $overdueCount overdue task(s). Say *\"push overdue to tomorrow\"* or *\"clean overdue tasks\"* to instantly clear your radar and reset your mental slate! 🌿",
                    "🎯 *Single Focus*: You have $overdueCount overdue task(s). Rather than tackling everything at once, say *\"push overdue to tomorrow\"* or tackle just one 25-minute sprint.",
                    "⚡ *Action Dissolves Anxiety*: Clear out stale tasks with *\"push overdue to tomorrow\"*, then pick your single highest priority objective."
                )
                getNextFromDeck(context, "advice_overdue", overduePool)
            }
            totalScreenTimeMins > 180 -> {
                val screenPool = listOf(
                    "🌿 *Screen Time Suggestion*: You've logged over ${totalScreenTimeMins / 60}h on your device today. Consider taking a 10-minute break away from screens, hydrating, or stretching to rest your eyes.",
                    "🧠 *Gentle Recharge*: High screen time naturally tires the eyes and mind. Stepping away for a short stroll or relaxing with some deep breathing can help you recharge.",
                    "💧 *Eye Rest*: Digital fatigue builds up quietly. Looking away into the distance for a few minutes or dimming display brightness can bring comfortable relief."
                )
                getNextFromDeck(context, "advice_screentime", screenPool)
            }
            pendingTasksCount == 0 -> {
                val clearPool = listOf(
                    "🎉 *Zen State*: All clear! You've achieved task nirvana for now. Take a well-deserved breather or do a quick mental arithmetic drill to keep your mind sharp.",
                    "✨ *Recharge Time*: Clean radar! Use this open space to reflect, hydrate, or plan tomorrow with zero stress.",
                    "🚀 *Victory Lap*: No fires, no pending tasks. Enjoy your flow state or relax guilt-free!"
                )
                getNextFromDeck(context, "advice_clear", clearPool)
            }
            else -> {
                val focusPool = listOf(
                    "⚡ *The 5-Minute Rule*: Tell yourself you'll only work on your task for five minutes. 80% of the time, once inertia is broken, you'll naturally keep going! 🎯",
                    "🧘 *Single-Tab Focus*: Close background distractions. Multitasking drops cognitive output by 40%. One objective at a time.",
                    "🌊 *Find Your Flow*: Pick your top task, silence alerts, and work uninterrupted. Deep work always beats long hours.",
                    "💡 *Energy Management*: Prioritize your hardest creative task during your peak energy hours. Guard your attention like gold."
                )
                getNextFromDeck(context, "advice_general", focusPool)
            }
        }
    }

    fun getBreathingGuidance(context: Context): String {
        val breathingPool = listOf(
            "🫁 *4-7-8 Box Breathing Protocol*:\n\n1. Inhale deeply through your nose for **4 seconds** 🌬️\n2. Hold your breath gently for **7 seconds** ⏸️\n3. Exhale slowly through your mouth for **8 seconds** 💨\n\n_Repeat 3 times to downregulate your nervous system and instantly regain mental clarity._",
            "🌿 *Resonance Breathing (6 Breaths/Min)*:\n\n• Inhale smoothly for **5 seconds** 🍃\n• Exhale smoothly for **5 seconds** 🍃\n\n_Doing this for just 60 seconds lowers cortisol and centers your focus immediately._",
            "⚡ *Physiological Sigh (Rapid Reset)*:\n\n1. Take two quick sniffs in through your nose (one deep, followed immediately by a sharp top-off sniff)\n2. Long, slow sigh out through your mouth\n\n_This expands deflated alveoli in your lungs and drops heart rate within seconds._"
        )
        return getNextFromDeck(context, "breathing_pool", breathingPool)
    }

    fun getFocusSprintGuidance(context: Context, minutes: Int = 25, taskTitle: String? = null): String {
        val taskStr = if (taskTitle != null) " on **$taskTitle**" else ""
        val pool = listOf(
            "⏱️ **$minutes-Minute Deep Focus Sprint Initiated!**$taskStr\n\n1. 🎯 **Single Objective**: Zero tab/app switching until the block completes.\n2. 🛡️ **App Armor**: Keep distracting apps locked.\n3. 🌊 **Cognitive Flow**: Friction in the first 3 minutes is normal. Breathe through it and momentum will take over.",
            "🔥 **Laser Mode Activated: $minutes Minutes**$taskStr\n\n• Turn device face-down or enable Do Not Disturb.\n• Eliminate all background audio except binaural beats or brown noise.\n• Lock in until the timer expires!",
            "⚡ **High-Performance Focus Block ($minutes mins)**$taskStr\n\n\"The ability to perform deep work is becoming increasingly rare at exactly the same time it is becoming increasingly valuable in our economy.\"\nDive in now—you've got this!"
        )
        return getNextFromDeck(context, "focus_sprint_pool", pool)
    }

    // =========================================================================
    // 7. COMPREHENSIVE FOCUS STATUS BRIEFING
    // =========================================================================
    fun getFocusStatusBriefing(
        context: Context,
        totalScreenTimeMins: Int,
        topAppName: String?,
        topAppMins: Int,
        activeRoutineName: String?,
        routineEndsAt: String?,
        restrictedAppsCount: Int,
        isStrictMode: Boolean,
        streak: Int,
        isVacation: Boolean,
        pendingCount: Int,
        completedTodayCount: Int,
        overdueCount: Int,
        totalHabitsCount: Int = 0,
        completedHabitsCount: Int = 0,
        notesCount: Int = 0,
        pinnedNotesCount: Int = 0,
        batteryPct: Int? = null,
        isCharging: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.append("⚡ *__Ayva's Focus Posture Report__*\n\n")

        // Battery telemetry if available
        if (batteryPct != null) {
            val chargeIcon = if (isCharging) "🔌" else if (batteryPct <= 20) "🪫" else "🔋"
            val chargeState = if (isCharging) "Charging" else if (batteryPct <= 20) "Low battery" else "Discharging"
            sb.append("$chargeIcon **Device Battery**: $batteryPct% ($chargeState)\n")
        }

        // 1. Routine status
        if (activeRoutineName != null) {
            val endStr = if (routineEndsAt != null) " (Active until $routineEndsAt)" else ""
            sb.append("🛡️ **Active Routine**: **$activeRoutineName**$endStr\n")
        } else {
            sb.append("🛡️ **Routine**: No active schedule right now\n")
        }

        // 2. App Armor
        val modeStr = if (isStrictMode) "Strict Mode" else "Soft Mode"
        if (restrictedAppsCount > 0) {
            sb.append("🔒 **App Armor**: $restrictedAppsCount apps restricted [$modeStr]\n")
        } else {
            sb.append("🔓 **App Armor**: Unrestricted (No apps locked)\n")
        }

        // 3. Streak & Vacation
        if (isVacation) {
            sb.append("🏖️ **Streak**: $streak days (Frozen in Vacation Mode ❄️)\n")
        } else if (streak > 0) {
            sb.append("🔥 **Streak**: $streak days active momentum\n")
        } else {
            sb.append("🌱 **Streak**: Ready to build fresh momentum today\n")
        }

        // 4. Tasks
        val overdueWarning = if (overdueCount > 0) " (⚠️ $overdueCount overdue!)" else ""
        sb.append("📋 **Tasks**: $completedTodayCount completed today, $pendingCount pending$overdueWarning\n")

        // 5. Habits (if any tracked)
        if (totalHabitsCount > 0) {
            val habitPct = (completedHabitsCount * 100) / totalHabitsCount
            val habitIcon = if (completedHabitsCount >= totalHabitsCount) "🌟" else "🌱"
            sb.append("$habitIcon **Habits**: $completedHabitsCount / $totalHabitsCount completed today ($habitPct%)\n")
        }

        // 6. Notes Overview (if any stored)
        if (notesCount > 0) {
            val pinnedStr = if (pinnedNotesCount > 0) " ($pinnedNotesCount pinned 📌)" else ""
            sb.append("📝 **Notes**: $notesCount notes stored$pinnedStr\n")
        }

        // 7. Screen Time
        val hrs = totalScreenTimeMins / 60
        val mins = totalScreenTimeMins % 60
        val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        val topAppStr = if (topAppName != null && topAppMins > 0) {
            val tHrs = topAppMins / 60
            val tMins = topAppMins % 60
            val tStr = if (tHrs > 0) "${tHrs}h ${tMins}m" else "${tMins}m"
            " • Top: $topAppName ($tStr)"
        } else ""
        sb.append("📱 **Screen Time**: $timeStr$topAppStr\n\n")

        // 8. Ayva Verdict
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val verdictPool = when {
            hour >= 22 || hour < 5 -> listOf(
                "🌙 _Suggestion: It's late into the evening. Be gentle on yourself tonight and wind down whenever you're ready._",
                "🍵 _Suggestion: Late night hours! Rest is cognitive fuel for tomorrow—feel free to rest your eyes and recharge._",
                "🌌 _Suggestion: Time to wind down. Great work navigating today—tomorrow brings a clean slate._"
            )
            overdueCount > 0 -> listOf(
                "🧹 _Suggestion: $overdueCount overdue items detected. Tap **Clean Overdue** below or say \"push overdue to tomorrow\" to clear your radar!_",
                "🎯 _Suggestion: Don't let overdue items weigh on you. Postpone them with smart triage and lock in a 25-minute focus block!_",
                "⚡ _Suggestion: Action dissolves overwhelm. Clean overdue items or tackle your top priority with laser focus._"
            )
            totalHabitsCount > 0 && completedHabitsCount < totalHabitsCount -> listOf(
                "✨ _Status: You have active habits waiting today. Checking even one off builds great daily flow!_",
                "🌱 _Status: Keep your daily chain intact! Log one habit now to lock in your daily streak._",
                "🔥 _Status: Habit progress at ${(completedHabitsCount * 100) / totalHabitsCount}%. A quick check-in will keep your momentum soaring._"
            )
            activeRoutineName != null -> listOf(
                "🛡️ _Status: Routine active ($activeRoutineName). Apps are gently shielded so you can focus peacefully._",
                "⚔️ _Status: Deep Work armor on! Your apps are protected under '$activeRoutineName'._"
            )
            restrictedAppsCount == 0 && totalScreenTimeMins > 180 -> listOf(
                "🌿 _Suggestion: Screen time has been high today. If you find yourself switching apps often, taking a short break or locking your top distractor might help._",
                "🚶 _Suggestion: Over 3 hours of screen time. Consider stepping away for 5 minutes, hydrating, or stretching your eyes._"
            )
            pendingCount == 0 -> listOf(
                "✨ _Status: All tasks clear for now. Great pace today—enjoy your free time!_",
                "🎉 _Status: Zero pending tasks! Outstanding focus today—relax or sharpen your mind with a mental drill._"
            )
            else -> listOf(
                "🎯 _Suggestion: Pick your single most meaningful task and take it step by step._",
                "⚡ _Suggestion: Focus on the next 20 minutes. One dedicated sprint moves mountains._",
                "🌿 _Suggestion: Protect your attention, work calmly on one objective, and ignore the noise._"
            )
        }
        val verdict = getNextFromDeck(context, "briefing_verdict", verdictPool)
        sb.append(verdict)
        return sb.toString()
    }

    fun getTaskCompletedPraise(context: Context, taskTitle: String, remainingCount: Int): String {
        val remainingStr = if (remainingCount == 0) {
            "Radar completely clear! 🎉"
        } else {
            "$remainingCount task${if (remainingCount > 1) "s" else ""} remaining."
        }
        val pool = listOf(
            "✅ *Crushed it!* \"$taskTitle\" is done. $remainingStr ⚡",
            "🎯 *Boom!* Marked \"$taskTitle\" as complete. $remainingStr Keep that momentum rollin'!",
            "🔥 *Task Conquered!* \"$taskTitle\" checked off. $remainingStr",
            "⚡ *Zero Friction!* Completed \"$taskTitle\". $remainingStr One step closer to total victory."
        )
        return getNextFromDeck(context, "task_complete_praise", pool)
    }

    fun getOverdueTriagePraise(context: Context, count: Int, targetTimeStr: String): String {
        val pool = listOf(
            "🧹 *Radar Cleaned!* Successfully pushed $count overdue task${if (count > 1) "s" else ""} to **$targetTimeStr**. Mental slate wiped clean! 🌿",
            "✨ *Triage Complete!* Rescheduled $count overdue item${if (count > 1) "s" else ""} to **$targetTimeStr**. Breathe easy—take them one at a time.",
            "⏰ *Forward Momentum!* Adjusted $count overdue task${if (count > 1) "s" else ""} for **$targetTimeStr**. Overwhelm dissolved."
        )
        return getNextFromDeck(context, "overdue_triage_praise", pool)
    }

    fun getOverdueCleanPraise(context: Context, count: Int): String {
        val pool = listOf(
            "🧹 *Slate Wiped Clean!* Removed $count overdue task${if (count > 1) "s" else ""} from your radar. Fresh start ahead! ✨",
            "✨ *Overdue Clutter Cleared!* Purged $count stale task${if (count > 1) "s" else ""}. Zero mental baggage—focus only on what matters right now.",
            "🕊️ *Peace of Mind Restored!* Dismissed $count overdue item${if (count > 1) "s" else ""}. Your radar is reset."
        )
        return getNextFromDeck(context, "overdue_clean_praise", pool)
    }

    // =========================================================================
    // 6. CONTINUOUS USAGE & SCREEN TIME BREAK REMINDERS
    // =========================================================================
    fun getNightSleepSuggestion(context: Context, appName: String): String {
        val pool = listOf(
            "🌙 You've been on $appName for 45m. A good night's sleep does wonders for your energy and mood tomorrow—time to rest your eyes whenever you're ready. ✨",
            "🌙 45m in $appName tonight. Deep, restorative sleep is the best gift for tomorrow's mind. Consider winding down and getting some cozy rest. 🍵",
            "🌙 It's late and you've spent 45m in $appName. Great sleep heals your brain and resets your energy. Gentle reminder to close your eyes and sleep well. 🛌",
            "🌙 45m on $appName. Restorative sleep sets you up for a peaceful, clear-headed morning. Be kind to your eyes and drift off when you can. 🌿",
            "🌙 Late night session in $appName (45m). Giving your mind real rest tonight pays off so much tomorrow. Sweet dreams whenever you decide to sleep. 🌌"
        )
        return getNextFromDeck(context, "night_sleep_suggestion", pool)
    }

    fun getDaySingleAppBreakSuggestion(context: Context, appName: String): String {
        val pool = listOf(
            "🌿 You've been using $appName for 90 minutes. Taking a quick 5-minute stroll or drinking a glass of water will leave you feeling refreshed!",
            "🚶 90m continuous session in $appName. Step away, stretch your back, or take a quick walk—your mind and body will thank you. ✨",
            "🌿 90 mins on $appName! A short breather away from the screen does wonders for focus and creativity. Take a gentle break whenever you reach a pause. ☕",
            "🚶 Great focus in $appName (90m)! How about giving your eyes a rest and taking a short walk outside or around the room? 🌿",
            "☕ You've spent 90m in $appName. A quick stretch, a glass of water, or looking out the window will recharge your clarity completely."
        )
        return getNextFromDeck(context, "day_single_app_break", pool)
    }

    fun getDayTotalScreenBreakSuggestion(context: Context): String {
        val pool = listOf(
            "🌿 You've had 2 continuous hours of screen time. Taking a short break to walk around, hydrate, and rest your eyes will recharge your energy! 🚶",
            "🚶 2 hours of continuous screen time. Step outside or take a quick stretch break—giving your eyes natural light works miracles for focus. ✨",
            "🌿 Screen time reached 2 continuous hours. A gentle reminder to pause, stretch your neck and shoulders, and take a refreshing breath of air. ☕",
            "🚶 2-hour screen milestone! Time for a mindful screen pause. Grab a glass of water and enjoy a few minutes off screens. 🌿",
            "☕ You've been active on your phone for 2 hours straight. Unplug for a brief walk—your clarity and vitality will reset beautifully."
        )
        return getNextFromDeck(context, "day_total_screen_break", pool)
    }

    // =========================================================================
    // 8. HABITS & NOTES CONVERSATIONAL FEEDBACK
    // =========================================================================
    fun getHabitCompletedPraise(context: Context, habitTitle: String, currentStreak: Int): String {
        val streakText = if (currentStreak > 1) " Streak is at **$currentStreak days**! 🔥" else " That's day 1 locked in! 🌱"
        val pool = listOf(
            "🌟 *Habit Check!* Logged \"$habitTitle\".$streakText Consistency builds champions.",
            "🌱 *Progress logged!* Marked \"$habitTitle\" complete.$streakText Keep building this habit!",
            "🔥 *Boom!* Completed \"$habitTitle\".$streakText Your daily momentum is unstoppable.",
            "✨ *Nailed it!* \"$habitTitle\" is checked off today.$streakText Future you will be proud."
        )
        return getNextFromDeck(context, "habit_complete_praise", pool)
    }

    fun getNoteCreatedQuip(context: Context, noteTitle: String): String {
        val pool = listOf(
            "📝 *Penned down!* Created note \"$noteTitle\". Safely stored and easy to find whenever you need it. ✨",
            "💡 *Captured!* Stored \"$noteTitle\" in your Keep Notes. Great ideas deserve a safe haven.",
            "📌 *Noted!* \"$noteTitle\" is secured in your notes. Ask me to search or read it anytime! 🌿"
        )
        return getNextFromDeck(context, "note_create_quip", pool)
    }

    fun getNotesSearchIntro(context: Context, query: String, count: Int): String {
        val pool = if (count == 0) {
            listOf(
                "🔍 *No notes found matching \"$query\".* Try a different keyword or create a new note with `/note <title>`!",
                "📝 Looked through your notes for \"$query\", but came up empty. Want to jot down a new note for it?",
                "💡 Couldn't find any notes mentioning \"$query\". Let me know if you'd like to create one!"
            )
        } else {
            listOf(
                "📝 *Found $count note${if (count > 1) "s" else ""} matching \"$query\":*",
                "🔍 *Here are the notes matching \"$query\":*",
                "📌 *Notes search results for \"$query\":*"
            )
        }
        return getNextFromDeck(context, "notes_search_intro", pool)
    }

    fun getNotesListIntro(context: Context, count: Int, pinnedCount: Int): String {
        val pool = if (count == 0) {
            listOf(
                "📝 *Your notes vault is currently empty.* Tap the button below or type `/note <title>` to write your first note! ✨",
                "💡 No notes stored yet. Jot down your thoughts or checklists anytime with `/note <title>`!"
            )
        } else {
            val pinnedText = if (pinnedCount > 0) " ($pinnedCount pinned 📌)" else ""
            listOf(
                "📝 *Here are your Keep Notes ($count total$pinnedText):*",
                "📌 *Your Stored Notes ($count total$pinnedText):*",
                "💡 *Notes Vault ($count stored$pinnedText):*"
            )
        }
        return getNextFromDeck(context, "notes_list_intro", pool)
    }

    fun getHabitListIntro(context: Context, count: Int, completedToday: Int): String {
        val pool = if (count == 0) {
            listOf(
                "🌱 *No habits tracked yet.* Building positive micro-habits transforms daily focus. Tap below to create your first habit!",
                "✨ You haven't added any habits to your tracker yet. Start with one simple daily goal like hydration or reading."
            )
        } else if (completedToday >= count) {
            listOf(
                "🌟 *Unstoppable!* You've completed all $count habits for today! Outstanding daily consistency. 🔥",
                "🏆 *All habits checked off today!* You're building ironclad momentum.",
                "✨ 100% habit completion today ($completedToday/$count). Enjoy this victory!"
            )
        } else {
            listOf(
                "🌱 *Your Daily Habit Tracker* ($completedToday/$count checked in today):",
                "⚡ *Today's Habits* ($completedToday of $count completed):",
                "🔥 *Habit Momentum* ($completedToday/$count checked off):"
            )
        }
        return getNextFromDeck(context, "habit_list_intro", pool)
    }

    fun getConversationalChatReply(context: Context?, userQuery: String): String {
        val clean = userQuery.lowercase().trim()
        val pool = when {
            clean in listOf("hi", "hello", "hey", "ayva", "hey ayva", "hi ayva", "good morning", "good evening", "good afternoon", "greetings", "yo", "sup") -> listOf(
                "✨ Hey there! I'm Ayva, your personal focus & productivity companion. How can I help you conquer today?",
                "👋 Hello! Ready to dive into your tasks, lock distracting apps, or do a quick mental sharpen?",
                "🌟 Greetings! What would you like to focus on right now? I'm here to support your flow.",
                "🌱 Welcome back! Let's protect your flow and make today meaningful. What's on your agenda?"
            )
            clean.contains("how are you") || clean.contains("how're you") || clean.contains("how r u") -> listOf(
                "🌿 I'm running smoothly and always ready to guard your flow state! How is your focus feeling today?",
                "⚡ Fully charged and ready for action! What are we conquering next?",
                "✨ Doing great! Looking forward to helping you make today focused, calm, and productive."
            )
            clean.contains("thank") -> listOf(
                "🙏 Always a pleasure! You're doing the hard work—I'm just here to keep the road clear.",
                "✨ Anytime! Keep that momentum going strong.",
                "🌱 You've got this! Let me know whenever you need anything else."
            )
            clean.contains("who are you") || clean.contains("what can you do") || clean.contains("what are you") -> listOf(
                "🛡️ I am **Ayva**, your built-in offline focus coach & assistant.\n\nI help you:\n• 🔒 Shield distracting apps with strict or soft app armor\n• 📋 Capture and schedule tasks with natural language\n• 🌱 Build streaks with daily habit tracking\n• 📝 Jot, search, and pin Keep Notes\n• 🧮 Sharpen mental clarity with interactive arithmetic drills\n• 🫁 Recharge with guided breathwork loops",
                "✨ I'm **Ayva**—an offline-first assistant designed to protect your attention, organize your tasks & notes, and keep your daily habits thriving with zero cloud reliance!"
            )
            clean.contains("joke") || clean.contains("tell me a joke") -> listOf(
                "😄 Why do programmers prefer dark mode? Because light attracts bugs! 🐛",
                "💻 Why did the smartphone wear glasses? Because it lost its contacts! 👓",
                "⏳ Procrastination is like a credit card: a lot of fun until you get the bill! Let's tackle that first task. 😉",
                "☕ Why don't programmers like nature? It has too many bugs and not enough coffee."
            )
            clean.contains("tired") || clean.contains("exhausted") || clean.contains("burnout") || clean.contains("sleepy") || clean.contains("no energy") -> listOf(
                "🍵 Energy ebbs and flows naturally. If you're feeling depleted, try a 5-minute break away from screens, drink a tall glass of cold water, or try our 4-7-8 breathing protocol. Be kind to yourself today.",
                "🌿 Your well-being comes first. High cognitive work requires deep recovery. Consider picking just one small, low-friction task or postponing heavy deadlines to tomorrow morning.",
                "🫁 When exhaustion hits, pushing harder often backfires. Take three slow physiological sighs (two quick sniffs in, one long slow exhale out) and let your body reset."
            )
            clean.contains("overwhelm") || clean.contains("stressed") || clean.contains("anxious") || clean.contains("panic") || clean.contains("too much") -> listOf(
                "🌊 Take a deep breath. Overwhelm happens when we try to hold tomorrow's problems in today's mind. What is the single smallest thing you can finish in the next 3 minutes?",
                "🎯 Break the spiral: Close your eyes for 30 seconds. Pick *just one* single task from your radar and ignore everything else. Action cures anxiety.",
                "🛡️ You don't have to do it all right now. Triage mode: let's reschedule non-urgent tasks and lock out social media so you have breathing room."
            )
            clean.contains("motivat") || clean.contains("inspire") || clean.contains("quote") || clean.contains("give me a boost") -> listOf(
                "🔥 \"Action doesn't come from motivation; motivation comes from action.\" Start with just 120 seconds on your task—momentum will carry you forward.",
                "⚡ \"You do not rise to the level of your goals. You fall to the level of your systems.\" — James Clear. Trust your routines today!",
                "🌱 \"Small disciplines repeated with consistency every day lead to great achievements gained slowly over time.\" Keep showing up!"
            )
            clean.contains("pomodoro") || clean.contains("technique") || clean.contains("how to focus") || clean.contains("study tips") -> listOf(
                "⏱️ **The Classic Pomodoro Flow**:\n1. Choose 1 single task.\n2. Set a timer for **25 minutes** and lock all distracting apps.\n3. Work with zero interruptions until the chime.\n4. Take a **5-minute screen-free break**.\n5. After 4 cycles, take a long 20-minute rest.",
                "🧠 **Deep Work Principles**:\n• **Single-tasking**: Context switching burns up to 40% of your cognitive energy.\n• **Friction rule**: Make distractions 20 seconds harder to reach (lock them in Strict Mode!).\n• **Protected windows**: Block 90-minute blocks during your peak circadian hours."
            )
            clean.contains("breath") || clean.contains("breathe") || clean.contains("calm down") || clean.contains("relax") || clean.contains("4-7-8") -> listOf(
                "🫁 **4-7-8 Parasympathetic Reset Protocol**:\n1. **Inhale quietly** through your nose for **4 seconds**.\n2. **Hold your breath** gently for **7 seconds**.\n3. **Exhale completely** through your mouth with a soft whoosh for **8 seconds**.\nRepeat 4 times to instantly down-regulate nervous system arousal.",
                "🌬️ **Physiological Sigh (Fastest Stress Reliever)**:\n1. Take two consecutive inhales through your nose: one deep breath, followed by an immediate sharp top-off sniff.\n2. One long, slow, effortless exhale through your mouth.\nRepeat 2-3 times to pop open collapsed alveoli and lower heart rate."
            )
            clean.contains("adhd") || clean.contains("distract") || clean.contains("can't focus") || clean.contains("cant focus") || clean.contains("procrastinat") -> listOf(
                "🧠 **ADHD & Dopamine Friction Strategies**:\n\n• **Body Doubling**: Put a 25-minute timer on or work alongside someone.\n• **The 2-Minute Rule**: Don't commit to finishing the task; only commit to 120 seconds of engagement.\n• **Friction Barrier**: Put your phone in another room or turn on Ayva's Strict Lock.\n• **Novelty Switch**: Change your physical workstation (standing, different desk, or outside).",
                "⚡ **Dopamine Priming for High-Friction Tasks**:\n1. Do a 30-second math drill (`/drill`) to wake up working memory.\n2. Clear out mental clutter with `/breathe`.\n3. Write down only **ONE** single micro-step on a piece of paper.\n4. Dive in with `/focus 25`!"
            )
            clean.contains("sleep") || clean.contains("insomnia") || clean.contains("night routine") || clean.contains("cant sleep") || clean.contains("can't sleep") -> listOf(
                "🌙 **Neuroscience Sleep Hygiene Protocol**:\n\n1. **No blue screens 60m before bed**: Melatonin suppression delays sleep onset by up to 90 minutes.\n2. **Cool temperature**: Keep your sleep environment slightly cool (around 18-20°C / 65-68°F).\n3. **Brain Dump**: Write down all lingering thoughts with `/note Tomorrow Ideas` so your subconscious can relax.\n4. **Physiological Sighs**: Take 5 slow sighs in bed to trigger drowsiness.",
                "🍵 **Nighttime Wind-Down Checklist**:\n• Turn on Do Not Disturb / Night Mode.\n• Sip chamomile or warm caffeine-free herbal tea.\n• Read a physical book or listen to calming audio.\n• Rest your eyes and allow your brain to clear metabolic waste through deep sleep."
            )
            clean.contains("habit") || clean.contains("routine") || clean.contains("atomic") || clean.contains("streak") -> listOf(
                "🌱 **Atomic Habits Blueprint (James Clear)**:\n\n1. **Make it obvious**: Cue your habit with an existing routine (e.g., *After I brew morning coffee, I review Ayva tasks*).\n2. **Make it attractive**: Pair habits with positive rewards.\n3. **Make it easy**: Reduce the friction down to 2 minutes.\n4. **Make it satisfying**: Track it immediately in Ayva Habit Tracker to lock in the dopamine checkmark!",
                "🔥 **The Power of Streaks**:\nNever miss twice! If you miss a single day, treat the next day as a top priority keystone check-in to preserve your neurological momentum."
            )
            else -> listOf(
                "💭 I'm listening! You can ask me to add tasks, search notes, check habits, lock apps, or view today's focus posture.",
                "🎯 Every small step counts. Tell me what you'd like to work on or organize today!",
                "✨ Ayva here. Feel free to check `/status`, review `/tasks`, search `/notes`, or start a quick `/drill`."
            )
        }
        return getNextFromDeck(context, "conversational_reply", pool)
    }
}

