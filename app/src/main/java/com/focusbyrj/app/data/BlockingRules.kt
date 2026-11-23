package com.focusbyrj.app.data

/** Every applicable rule is retained; limits are evaluated before choosing Hard over Soft. */
internal object BlockingRules {
    data class Rule(
        val packageName: String,
        val source: String,
        val mode: String,
        val restrictionMode: String,
        val minutes: Int = 0,
        val clicks: Int = 0,
        val quote: String = "",
    ) {
        val label: String
            get() =
                "$source: ${if (mode.equals("SOFT", true)) "Soft" else "Hard"}" +
                    when (restrictionMode) {
                        "TIME_LIMIT" -> " after ${minutes}m"
                        "CLICK_LIMIT" -> " after $clicks opens"
                        else -> ""
                    }
    }

    private fun mode(value: String) = if (value.equals("SOFT", true)) "SOFT" else "HARD"

    fun manual(row: AppRestriction) =
        Rule(
            row.packageName,
            "Manual",
            mode(row.mode),
            row.restrictionMode,
            row.timeLimitMinutes,
            row.clickLimitCount,
            row.customQuote,
        )

    fun routines(schedules: List<FocusSchedule>, at: java.util.Calendar): List<Rule> =
        schedules
            .filter { it.isActiveAt(at) }
            .sortedBy { it.id }
            .flatMap { schedule ->
                schedule.appsToBlock.split(',').mapNotNull { entry ->
                    val parts = entry.trim().split('|')
                    val pkg = parts.first().trim()
                    if (pkg.isBlank()) null
                    else
                        Rule(
                            pkg,
                            "Routine ‘${schedule.name}’",
                            mode(
                                parts.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
                                    ?: schedule.mode
                            ),
                            schedule.restrictionMode,
                            schedule.timeLimitMinutes,
                            schedule.clickLimitCount,
                        )
                }
            }

    private val priority =
        compareBy<Rule> { if (it.mode.equals("SOFT", true)) 1 else 0 }
            .thenBy { it.source }
            .thenBy { it.restrictionMode }
            .thenBy { it.minutes }
            .thenBy { it.clicks }

    fun preview(rules: List<Rule>): Rule? = rules.minWithOrNull(priority)

    fun resolve(
        rules: List<Rule>,
        usageMinutes: (Int) -> Int,
        launchCount: () -> Int,
        temporarilyUnlocked: Boolean = false,
    ): Rule? =
        rules
            .filter { rule ->
                (!temporarilyUnlocked || !rule.mode.equals("SOFT", true)) &&
                    when (rule.restrictionMode) {
                        "TIME_LIMIT" ->
                            rule.minutes > 0 && usageMinutes(rule.minutes) >= rule.minutes
                        "CLICK_LIMIT" -> rule.clicks > 0 && launchCount() > rule.clicks
                        else -> true
                    }
            }
            .minWithOrNull(priority)

    fun summary(rules: List<Rule>): String =
        rules.sortedWith(priority).joinToString(" · ") { it.label } +
            if (rules.size > 1) ". Hard takes priority once its rule applies." else ""
}
