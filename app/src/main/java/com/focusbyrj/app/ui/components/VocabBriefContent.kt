package com.focusbyrj.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Collections

private val processedVocabCardIds = Collections.synchronizedSet(mutableSetOf<String>())

@Composable
fun VocabBriefContent(
    vocabJson: String,
    fontSizeSp: Float,
    isLearnMoreSession: Boolean,
    messageId: String? = null,
    onLearnMoreClick: () -> Unit,
    onQuizClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val jsonObj = remember(vocabJson) {
        try {
            JSONObject(vocabJson)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    val idiomObj = remember(jsonObj) { jsonObj.optJSONObject("idiom") }
    val owsObj = remember(jsonObj) { jsonObj.optJSONObject("ows") }
    val revIdiomObj = remember(jsonObj) { jsonObj.optJSONObject("rev_idiom") }
    val revOwsObj = remember(jsonObj) { jsonObj.optJSONObject("rev_ows") }

    var showButtons by remember(messageId) {
        mutableStateOf(isLearnMoreSession || (messageId != null && !messageId.startsWith("temp_")))
    }

    LaunchedEffect(messageId) {
        if (!showButtons) {
            delay(2000)
            showButtons = true
        }
    }

    // Confirm words learned and cycle revision items when the user actually views this card
    // Guarded to only execute once per card instance so scrolling chat list does not bump spaced repetition repeatedly
    LaunchedEffect(vocabJson, messageId) {
        val cardKey = messageId ?: vocabJson.hashCode().toString()
        if (!processedVocabCardIds.add(cardKey)) {
            return@LaunchedEffect
        }

        val idiomId = idiomObj?.optInt("id", -1)?.takeIf { it > 0 }
        val owsId = owsObj?.optInt("id", -1)?.takeIf { it > 0 }
        val revIdiomId = revIdiomObj?.optInt("id", -1)?.takeIf { it > 0 }
        val revOwsId = revOwsObj?.optInt("id", -1)?.takeIf { it > 0 }

        if (idiomId != null || owsId != null || revIdiomId != null || revOwsId != null) {
            val app = context.applicationContext as? com.focusbyrj.app.FocusApplication
            app?.vocabRepository?.let { repo ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (idiomId != null) repo.markIdiomLearned(idiomId)
                    if (owsId != null) repo.markOwsLearned(owsId)
                    repo.touchRevision(revIdiomId, revOwsId)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (revIdiomObj != null || revOwsObj != null) {
            Text(
                text = "🔄 Spaced Review (Retention Queue):",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (fontSizeSp * 0.85f).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (revIdiomObj != null) {
                VocabItemCard(
                    title = "Idiom",
                    word = revIdiomObj.optString("idiom", ""),
                    meaning = revIdiomObj.optString("meaning", ""),
                    fontSizeSp = fontSizeSp,
                    isRevision = true
                )
            }
            if (revOwsObj != null) {
                VocabItemCard(
                    title = "OWS",
                    word = revOwsObj.optString("term", ""),
                    meaning = revOwsObj.optString("definition", ""),
                    fontSizeSp = fontSizeSp,
                    isRevision = true
                )
            }
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
        }

        Text(
            text = "✨ Today's New Words:",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = (fontSizeSp * 0.85f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (idiomObj != null) {
            VocabItemCard(
                title = "Idiom",
                word = idiomObj.optString("idiom", ""),
                meaning = idiomObj.optString("meaning", ""),
                fontSizeSp = fontSizeSp,
                isRevision = false
            )
        }
        if (owsObj != null) {
            VocabItemCard(
                title = "OWS",
                word = owsObj.optString("term", ""),
                meaning = owsObj.optString("definition", ""),
                fontSizeSp = fontSizeSp,
                isRevision = false
            )
        }

        AnimatedVisibility(
            visible = showButtons,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLearnMoreClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(if (isLearnMoreSession) "Next" else "More words", fontSize = (fontSizeSp * 0.9f).sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onQuizClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (isLearnMoreSession) "End" else "Quiz", fontSize = (fontSizeSp * 0.9f).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VocabItemCard(
    title: String,
    word: String,
    meaning: String,
    fontSizeSp: Float,
    isRevision: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isRevision) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.75f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (fontSizeSp * 1.1f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            text = meaning,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = (fontSizeSp * 0.95f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
