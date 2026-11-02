package com.focusbyrj.app.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared editor chrome; changes stay in the caller's draft until Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetEditor(
    title: String,
    sections: List<String>,
    previewSummary: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    saveLabel: String = "Apply",
    error: String? = null,
    preview: @Composable () -> Unit,
    content: @Composable ColumnScope.(Int) -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var previewExpanded by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(selected) { scroll.scrollTo(0) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
                actions = { TextButton(onClick = onSave) { Text(saveLabel) } })
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
            if (sections.size > 1) TabRow(selectedTabIndex = selected) {
                sections.forEachIndexed { index, label ->
                    Tab(selected = selected == index, onClick = { selected = index }, text = { Text(label) })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
                WidgetEditorRow("Preview", previewSummary, onClick = { previewExpanded = !previewExpanded },
                    trailing = { Icon(if (previewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (previewExpanded) "Hide preview" else "Show preview") })
                if (previewExpanded) {
                    preview()
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(8.dp))
                content(selected)
            }
        }
    }
}

@Composable
internal fun WidgetEditorRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = { Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp)) }
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
internal fun <T> WidgetChoiceRow(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    swatch: ((T) -> Color)? = null,
    detail: ((T) -> String?)? = null,
    selectedLabel: String = label(value)
) {
    var open by rememberSaveable { mutableStateOf(false) }
    WidgetEditorRow(title, selectedLabel, onClick = { open = true }, enabled = options.isNotEmpty())
    if (open) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { open = false }) { Text("Close") }
                    }
                    if (options.isEmpty()) Text("No matches", style = MaterialTheme.typography.bodySmall)
                    val listHeight = (options.size * if (detail != null) 64 else 48).coerceIn(48, 360).dp
                    LazyColumn(Modifier.fillMaxWidth().height(listHeight)
                        .testTag("widget_editor_choices")) {
                        items(options) { option ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = option == value, role = Role.RadioButton,
                                    onClick = { onSelect(option); open = false }),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RadioButton(option == value, onClick = null)
                                swatch?.let { color -> Surface(color = color(option), shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(18.dp)) {} }
                                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(label(option), style = MaterialTheme.typography.bodyMedium)
                                    detail?.invoke(option)?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WidgetSliderRow(title: String, value: Int, suffix: String, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text("$value$suffix", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
            thumb = { Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
            track = { state -> SliderDefaults.Track(sliderState = state, modifier = Modifier.height(4.dp),
                drawStopIndicator = null, thumbTrackGapSize = 0.dp) })
    }
}
