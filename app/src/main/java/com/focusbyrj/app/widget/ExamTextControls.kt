package com.focusbyrj.app.widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
private fun TextChoices(value: String, options: List<Pair<String,String>>, fonts: Boolean = false, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        options.forEach { (id,title) -> FilterChip(value==id,onClick={onSelect(id)},modifier=Modifier.weight(1f),label={
            Text(title,style=MaterialTheme.typography.labelMedium,fontFamily=if(fonts) when(id){"SERIF"->FontFamily.Serif;"MONO"->FontFamily.Monospace;else->FontFamily.SansSerif} else FontFamily.Default)
        }) }
    }
}

@Composable
internal fun ExamTextControls(draft: ExamCountdown, change: (ExamCountdown) -> Unit) {
    val listing=draft.displayMode=="LIST" && runCatching{draft.safePreview().visibleExams().size>1}.getOrDefault(draft.exams().size>1)
    val scale=if(listing) when { draft.listTextSize<=14->"COMPACT";draft.listTextSize<=16->"BALANCED";else->"LARGE" }
        else when {draft.textSize<=48->"COMPACT";draft.textSize<=72->"BALANCED";else->"LARGE"}
    ExamGroup("Text & layout") {
        Text("Text size",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=12.dp))
        TextChoices(scale,listOf("COMPACT" to "Compact","BALANCED" to "Balanced","LARGE" to "Large")){change(draft.withTextScale(it))}
        Text("Font",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=8.dp))
        TextChoices(draft.font,listOf("SANS" to "Sans","SERIF" to "Serif","MONO" to "Mono"),fonts=true){change(draft.copy(font=it))}
        if(!listing) {
            Text("Alignment",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=8.dp))
            TextChoices(draft.align,listOf("START" to "Left","CENTER" to "Center","END" to "Right")){change(draft.copy(align=it))}
        }
        WidgetChoiceRow("Countdown format",draft.format,ExamCountdown.FORMATS,ExamWidgetPresentation::label,{change(draft.copy(format=it))})
        ExamMore("Fine-tune text") {
            if(listing) {
                WidgetSliderRow("List text size",draft.listTextSize," sp",11..20){change(draft.copy(listTextSize=it))}
                WidgetSliderRow("Maximum visible exams",draft.maxRows,"",1..6){change(draft.copy(maxRows=it))}
            } else {
                WidgetSliderRow("Countdown size",draft.textSize," sp",24..96){change(draft.copy(textSize=it))}
                WidgetSliderRow("Exam name size",draft.titleSize," sp",12..22){change(draft.copy(titleSize=it))}
            }
            WidgetSliderRow("Spacing",draft.padding," dp",8..28){change(draft.copy(padding=it))}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamVisibleDetails(draft: ExamCountdown, change: (ExamCountdown) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val summary=buildList {if(draft.showTitle)add("Name");if(draft.showDate)add("Date");if(draft.showTime)add("Time");if(draft.showVenue)add("Venue");if(draft.showBadge)add("Label");if(draft.showMessage)add("Message")}.joinToString(" · ")
    ExamGroup("On the widget") {
        WidgetEditorRow("Visible details",summary.ifBlank{"Countdown only"},onClick={open=true})
        if(draft.showDate) WidgetChoiceRow("Date format",draft.dateStyle,listOf("SHORT","LONG","ISO"),
            {when(it){"SHORT"->"Readable";"LONG"->"Full date";else->"Numeric"}},
            {change(draft.copy(dateStyle=it))},detail={ExamWidgetPresentation.date(draft.copy(dateStyle=it,showTime=false),true)})
        if(draft.showTime) WidgetChoiceRow("Clock format",draft.clockStyle,listOf("SYSTEM","12","24"),ExamWidgetPresentation::label,{change(draft.copy(clockStyle=it))})
    }
    if(open) ModalBottomSheet(onDismissRequest={open=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Visible details",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={open=false}){Text("Done")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={change(draft.withEssentialDetails())}){Text("Essentials")}
                OutlinedButton(onClick={change(draft.withEssentialDetails(time=true))}){Text("Date + time")}
            }
            WidgetToggleRow("Exam name",draft.showTitle){change(draft.copy(showTitle=it))}
            WidgetToggleRow("Exam date",draft.showDate){change(draft.copy(showDate=it))}
            WidgetToggleRow("Exam time",draft.showTime){change(draft.copy(showTime=it))}
            WidgetToggleRow("Venue",draft.showVenue){change(draft.copy(showVenue=it))}
            WidgetToggleRow("Exam label",draft.showBadge){change(draft.copy(showBadge=it))}
            WidgetToggleRow("Personal message",draft.showMessage){change(draft.copy(showMessage=it))}
        }
    }
}
