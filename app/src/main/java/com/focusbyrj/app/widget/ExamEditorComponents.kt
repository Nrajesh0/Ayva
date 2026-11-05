package com.focusbyrj.app.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Exam-specific chrome: other widget editors retain their existing interface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamEditor(
    onCancel: () -> Unit, onSave: () -> Unit, saveLabel: String, error: String?,
    preview: @Composable () -> Unit, content: @Composable ColumnScope.(Int) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    LaunchedEffect(tab) { scroll.scrollTo(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Exam widget", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } }) },
        bottomBar = { Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                Button(onClick = onSave, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(saveLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        } }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Exams", "Design", "Details").forEachIndexed { index, title ->
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = if (tab == index) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        modifier = Modifier.weight(1f).selectable(tab == index, role = Role.Tab, onClick = { tab = index })) {
                        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                            Text(title, style = MaterialTheme.typography.labelLarge,
                                color = if (tab == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                preview()
                content(tab)
            }
        }
    }
}

@Composable
internal fun ExamGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), content = content)
        }
    }
}

@Composable
internal fun ExamMore(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        WidgetEditorRow(title, if (expanded) "Less" else "More", onClick = { expanded = !expanded }, trailing = {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        })
        if (expanded) content()
    }
}

@Composable
internal fun ExamDesignPicker(value: ExamCountdown, onChange: (ExamCountdown) -> Unit) {
    var category by rememberSaveable { mutableStateOf(ExamCountdown.designCategory(value.design)) }
    LaunchedEffect(value.design) { category = ExamCountdown.designCategory(value.design) }
    val choices = (if (category == "Glass") ExamCountdown.GLASS_DESIGNS else ExamCountdown.BOLD_DESIGNS)
        .map { it to ExamWidgetPresentation.label(it) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose a layout", style = MaterialTheme.typography.titleSmall)
        if (value.design in ExamCountdown.LEGACY_DESIGNS) {
            Text("Current layout: ${ExamWidgetPresentation.label(value.design)}. Kept until you choose a new design.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Bold", "Glass").forEach { title ->
                FilterChip(category == title, onClick = { category = title }, label = { Text(title) }, modifier = Modifier.weight(1f))
            }
        }
        choices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (id, name) ->
                    val selected = value.design == id
                    Surface(shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) .5f else .2f),
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
                        modifier = Modifier.weight(1f).heightIn(min = 140.dp).selectable(selected, role = Role.RadioButton, onClick = { onChange(value.withDesign(id)) })) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.labelLarge)
                                if (selected) Icon(Icons.Default.Check, "Selected $name", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(14.dp))
                            if (id in listOf("POSTER", "ORBITAL")) ExamBoldThumbnail(id) else ExamModernThumbnail(id)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExamBoldThumbnail(design: String) {
    val accent = when (design) { "POSTER" -> Color(0xFFDFFF54); "ORBITAL" -> Color(0xFFBAAAFF); else -> Color(0xFFFF946F) }
    val background = if (design == "POSTER") accent else Color(0xFF181624)
    Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(10.dp)).background(background)) {
        Canvas(Modifier.fillMaxSize()) {
            if (design == "POSTER") drawPath(Path().apply { moveTo(size.width * .72f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height * .45f); close() }, Color(0xFF181624))
            if (design == "COLORBLOCK") drawRect(accent, size = androidx.compose.ui.geometry.Size(size.width * .46f, size.height))
            if (design == "ORBITAL") {
                val center = androidx.compose.ui.geometry.Offset(size.width * .27f, size.height * .5f)
                val radius = size.minDimension * .29f
                drawCircle(accent.copy(alpha = .18f), radius, center, style = Stroke(2.dp.toPx()))
                drawArc(accent, -90f, 240f, false, topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f), style = Stroke(2.dp.toPx()))
            }
        }
        if (design == "POSTER") Column(Modifier.padding(10.dp)) {
            Text("28", fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, color = Color.Black)
            Text("DAYS TO GO", fontSize = 7.sp, color = Color.Black)
            Text("YOUR NEXT EXAM", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("28", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = if (design == "COLORBLOCK") Color.Black else Color.White)
            }
            Column(Modifier.weight(1f).padding(4.dp)) {
                Text("YOUR\nEXAM", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("12 JUNE", fontSize = 7.sp, color = accent)
            }
        }
    }
}

@Composable
private fun ExamModernThumbnail(design: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = remember(design) { ExamCountdown().withDesign(design) }
    val colors = ExamCountdownProvider.surfaceColors(config, ExamCountdownProvider.colors(context, config))
    val surface = remember(config) { ExamCountdownProvider.background(config, colors, 340, 180, 58).asImageBitmap() }
    val wallpaper = remember { ExamPreviewWallpaper() }
    Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(10.dp))) {
        if (design in ExamCountdown.GLASS_DESIGNS) Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas -> wallpaper.setBounds(0,0,size.width.toInt(),size.height.toInt());wallpaper.draw(canvas.nativeCanvas) }
        }
        Image(surface, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
        if (design in listOf("BENTO", "LAYERS")) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("28", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = Color(if (design == "BENTO") ExamCountdownProvider.contrastInk(colors[2]) else colors[1]))
            }
            Column(Modifier.weight(1f).padding(6.dp)) {
                Text("YOUR\nEXAM", fontSize = 10.sp, lineHeight = 12.sp, color = Color(colors[1]), fontWeight = FontWeight.SemiBold)
                Text("12 JUNE", fontSize = 7.sp, color = Color(colors[1]).copy(alpha = .7f))
            }
        } else Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = if (design in listOf("AURORA", "PRISM")) Alignment.CenterHorizontally else Alignment.Start) {
            Text("28", fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Color(colors[1]),
                fontFamily = if (design == "SIGNAL") androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.SansSerif)
            Text("YOUR EXAM", fontSize = 8.sp, color = Color(colors[1]))
        }
    }
}

@Composable
internal fun ExamPalettePicker(value: ExamCountdown, onChange: (ExamCountdown) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val choices = when(ExamCountdown.designCategory(value.design)) {
        "Bold" -> listOf("CITRUS" to "Citrus", "IRIS" to "Iris", "CORAL" to "Coral", "THEME" to "App", "DARK" to "Ink", "LIGHT" to "Paper")
        "Glass" -> listOf("SLATE" to "Smoke", "OCEAN" to "Mist", "IRIS" to "Iris", "LIGHT" to "Paper", "DARK" to "Ink", "THEME" to "App")
        else -> listOf("THEME" to "App", "DARK" to "Ink", "LIGHT" to "Paper", "OCEAN" to "Mist", "FOREST" to "Sage", "ROSE" to "Clay")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Color", style = MaterialTheme.typography.titleSmall)
        choices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, title) ->
                    val selected = value.palette == id
                    val palette = value.copy(palette = id)
                    val colors = ExamCountdownProvider.surfaceColors(palette, ExamCountdownProvider.colors(context, palette))
                    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
                        color = Color(colors[0]), modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton,
                            onClick = { onChange(value.copy(palette = id)) })) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(Color(colors[2]), CircleShape))
                            Text(title, color = Color(colors[1]), style = MaterialTheme.typography.labelMedium)
                            if (selected) Icon(Icons.Default.Check, "Selected $title", Modifier.size(12.dp), tint = Color(colors[2]))
                        }
                    }
                }
            }
        }
    }
}
