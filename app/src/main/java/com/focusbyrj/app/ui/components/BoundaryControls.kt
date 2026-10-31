package com.focusbyrj.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusbyrj.app.data.AppRestriction

@Stable
class BoundarySelection {
    var query by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
    var selecting by mutableStateOf(false)
    var selected by mutableStateOf(setOf<String>())
    fun matches(app: AppRestriction): Boolean = query.trim().split(Regex("\\s+")).all {
        app.appName.contains(it, true) || app.packageName.contains(it, true) ||
            (if (app.isFromRoutine) "routine" else "manual").contains(it,true)
    }
    fun toggle(key: String) { selecting = true; selected = if (key in selected) selected-key else selected+key }
    fun finish() { selecting=false;selected=emptySet() }
    companion object {
        val Saver = mapSaver(
            save = { mapOf("query" to it.query, "search" to it.searchOpen, "selecting" to it.selecting, "selected" to it.selected.toList()) },
            restore = { values -> BoundarySelection().apply {
                query = values["query"] as String
                searchOpen = values["search"] as Boolean
                selecting = values["selecting"] as Boolean
                selected = (values["selected"] as List<*>).filterIsInstance<String>().toSet()
            } }
        )
    }

}

@Composable
fun BoundaryControls(state: BoundarySelection, apps: List<AppRestriction>, onDeleteMany: (List<AppRestriction>) -> Unit) {
    var pending by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(apps.map { it.packageName }) {
        state.selected = state.selected.intersect(apps.map { it.packageName }.toSet())
        if(apps.isEmpty()) state.finish()
    }
    BackHandler(state.selecting || state.searchOpen) {
        if(state.selecting) state.finish() else {state.searchOpen=false;state.query=""}
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Text(if(state.selecting) "${state.selected.size} selected" else "Your boundaries", Modifier.weight(1f),
            style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.selecting) {
            IconButton(onClick={state.finish()}) {Icon(Icons.Default.Close,"Cancel selection",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
        } else {
            IconButton(onClick={state.searchOpen=!state.searchOpen;if(!state.searchOpen)state.query=""}) {Icon(Icons.Default.Search,"Search boundaries",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(apps.isNotEmpty()) IconButton(onClick={state.selecting=true}) {Icon(Icons.Default.Checklist,"Select boundaries",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
    if(state.searchOpen) {
        OutlinedTextField(state.query,{state.query=it},Modifier.fillMaxWidth().testTag("boundary_search"),singleLine=true,
            placeholder={Text("Search apps or package names")},leadingIcon={Icon(Icons.Default.Search,null)},
            trailingIcon={IconButton(onClick={state.query="";state.searchOpen=false}){Icon(Icons.Default.Close,"Close boundary search")}},shape=RoundedCornerShape(16.dp))
        Spacer(Modifier.height(8.dp))
    }
    if(state.selecting) {
        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                val matching=apps.filter(state::matches)
                TextButton(onClick={state.selected=state.selected+matching.map{it.packageName}},enabled=matching.isNotEmpty()) {Text("Select all (${matching.size})")}
                TextButton(onClick={state.selected=emptySet()},enabled=state.selected.isNotEmpty()) {Text("Deselect all")}
                Spacer(Modifier.weight(1f))
                IconButton(onClick={pending=state.selected.toSet()},enabled=state.selected.isNotEmpty()) {Icon(Icons.Default.Delete,"Delete selected boundaries",tint=if(state.selected.isEmpty())MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)}
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if(pending!=null) {
        val targets=apps.filter{it.packageName in pending!!}
        AlertDialog(onDismissRequest={pending=null},title={Text("Delete ${targets.size} boundaries?")},
            text={Text(targets.take(3).joinToString { it.appName }+(if(targets.size>3)" and ${targets.size-3} more" else "")+" will be removed. Selected apps will also be removed from routines that include them.")},
            confirmButton={TextButton(onClick={onDeleteMany(targets);pending=null;state.finish()},enabled=targets.isNotEmpty()){Text("Delete",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.SemiBold)}},
            dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}})
    }
}
