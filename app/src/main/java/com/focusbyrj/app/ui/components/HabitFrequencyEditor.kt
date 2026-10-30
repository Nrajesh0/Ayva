package com.focusbyrj.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.data.HabitFrequency

@OptIn(ExperimentalLayoutApi::class)
@Composable fun HabitFrequencyEditor(value: HabitFrequency, onChange: (HabitFrequency) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("DAILY" to "Daily", "DAYS" to "Selected days", "WEEKLY" to "Days per week").forEach { (mode, label) ->
                FilterChip(selected = value.mode == mode, onClick = { onChange(value.copy(mode = mode, since = java.time.LocalDate.now().toString())) }, label = { Text(label) })
            }
        }
        if (value.mode == "DAYS") FlowRow {
            (1..7).forEach { day -> FilterChip(selected = day in value.days, onClick = {
                val days = if (day in value.days) value.days - day else value.days + day
                if (days.isNotEmpty()) onChange(value.copy(days = days, since = java.time.LocalDate.now().toString()))
            }, label = { Text(java.time.DayOfWeek.of(day).name.take(3)) }) }
        }
        if (value.mode == "WEEKLY") {
            Text("${value.weeklyTarget} different days each week · Monday–Sunday")
            Slider(value = value.weeklyTarget.toFloat(), onValueChange = { onChange(value.copy(weeklyTarget = it.toInt(), since = java.time.LocalDate.now().toString())) }, valueRange = 1f..7f, steps = 5)
            Text("Meet the daily target on each chosen day. Extra taps don't count as extra days.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
