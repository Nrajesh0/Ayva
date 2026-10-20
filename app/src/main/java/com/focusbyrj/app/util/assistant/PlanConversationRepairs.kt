package com.focusbyrj.app.util.assistant


internal enum class PlanRepairOperation(val key: String) {
    DURATION("duration"), WORK_TIME("schedule"), DEADLINE("deadline"), SHIFT("shift"),
    UNSCHEDULE("unschedule"), CLEAR_DEADLINE("clear_deadline")
}

internal sealed interface PlanRepair {
    val reference: String
    val value: String
    data class Change(override val reference: String, val operation: PlanRepairOperation, override val value: String = "") : PlanRepair
    data class TimeKind(override val reference: String, override val value: String) : PlanRepair
}

/** Interpret edits using a typed focus; this layer cannot mutate a workspace or saved task. */
internal object PlanConversationRepairs {
    fun parse(plan: PlanWorkspace, input: String): PlanRepair? {
        val text = PlanLanguage.normalize(input)
        Regex("(?i)^(?:give|allocate|make) (.+)$").matchEntire(text)?.let { m ->
            PlanLanguage.durationSuffix(m.groupValues[1])?.let { (reference, value) ->
                return PlanRepair.Change(reference, PlanRepairOperation.DURATION, value)
            }
        }
        Regex("(?i)^unschedule (.+)$").matchEntire(text)?.let {
            return PlanRepair.Change(it.groupValues[1], PlanRepairOperation.UNSCHEDULE)
        }
        Regex("(?i)^(?:clear|remove) (?:the )?(?:deadline|due date) (?:for |from )?(.+)$").matchEntire(text)?.let {
            return PlanRepair.Change(it.groupValues[1], PlanRepairOperation.CLEAR_DEADLINE)
        }
        val correction = PlanLanguage.correctionValue(text)
        val relative = Regex("(?i)^(?:(?:move|shift|push) )?(.+?) (earlier|later)$").matchEntire(correction ?: text)
        if (relative != null && (correction != null || Regex("(?i)^(?:move|shift|push) ").containsMatchIn(text))) {
            val body = relative.groupValues[1]
            val pair = PlanLanguage.durationSuffix(body)
                ?: PlanLanguage.duration(body)?.let { "that" to body }
            pair?.let { (reference, value) ->
                val delta = PlanLanguage.duration(value)!! * if (relative.groupValues[2].equals("earlier", true)) -1 else 1
                return PlanRepair.Change(reference, PlanRepairOperation.SHIFT, delta.toString())
            }
        }
        if (correction == null) return null
        val reference = plan.editFocus?.stepId ?: "that"
        if (PlanLanguage.duration(correction) != null) return PlanRepair.Change(reference, PlanRepairOperation.DURATION, correction)
        val expression = PlanTemporalLanguage.expression(correction, null, plan.timezone) ?: return null
        val operation = when {
            correction.startsWith("by ", true) -> PlanRepairOperation.DEADLINE
            correction.startsWith("on ", true) -> PlanRepairOperation.WORK_TIME
            plan.editFocus?.field == PlanEditField.DEADLINE -> PlanRepairOperation.DEADLINE
            plan.editFocus?.field == PlanEditField.WORK_TIME -> PlanRepairOperation.WORK_TIME
            else -> return PlanRepair.TimeKind(reference, expression)
        }
        val step = plan.steps.firstOrNull { it.id == plan.editFocus?.stepId }
        val date = if (operation == PlanRepairOperation.DEADLINE) step?.deadline else step?.start
        val value = PlanTemporalLanguage.expression(correction, date, plan.timezone) ?: return null
        return PlanRepair.Change(reference, operation, value)
    }
}
