// Daily Quests UI Component - v1.4.0
package com.focusbyrj.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.components.ChestRarity
import com.focusbyrj.app.ui.components.Duolingo3DButton
import com.focusbyrj.app.ui.components.DuolingoMysteryChestDialog
import com.focusbyrj.app.ui.components.EarlyBirdChestGraphic
import com.focusbyrj.app.ui.components.NightOwlChestGraphic
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.MysteryReward
import java.util.Calendar

@Composable
fun DailyQuestsCard(
    onDismiss: (() -> Unit)? = null
) {
    val questState by DailyQuestManager.stateFlow.collectAsState()
    val isDark = isSystemInDarkTheme()
    var showChestDialog by remember { mutableStateOf(false) }
    var selectedChestRarity by remember { mutableStateOf(ChestRarity.COMMON) }

    if (showChestDialog) {
        DuolingoMysteryChestDialog(
            initialRarity = selectedChestRarity,
            onDismiss = { showChestDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // ================= 1. EARLY BIRD CHEST CARD =================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF131F24),
            border = BorderStroke(1.dp, Color(0xFF20333D)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = questState.isEarlyBirdAvailable) {
                    selectedChestRarity = ChestRarity.COMMON
                    DailyQuestManager.markMorningChestClaimed()
                    showChestDialog = true
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EarlyBirdChestGraphic(
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Early Bird Chest",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 0.sp
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    when {
                        questState.morningChestClaimed -> {
                            Text(
                                text = "Claimed today ✓",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF58CC02)
                            )
                        }
                        questState.isEarlyBirdAvailable -> {
                            Text(
                                text = "Your reward chest is ready!",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFFFF9600)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Duolingo3DButton(
                                text = "CLAIM",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                buttonColor = Color(0xFFFF9600),
                                bevelColor = Color(0xFFD47800),
                                textColor = Color.White,
                                onClick = {
                                    selectedChestRarity = ChestRarity.COMMON
                                    DailyQuestManager.markMorningChestClaimed()
                                    showChestDialog = true
                                }
                            )
                        }
                        else -> {
                            Text(
                                text = "Do a test between 6:00am and 6:00pm to earn this chest.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ================= 2. NIGHT OWL CHEST CARD =================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF131F24),
            border = BorderStroke(1.dp, Color(0xFF20333D)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = questState.isNightOwlAvailable) {
                    selectedChestRarity = ChestRarity.RARE
                    DailyQuestManager.markEveningChestClaimed()
                    showChestDialog = true
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NightOwlChestGraphic(
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Night Owl Chest",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 0.sp
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    when {
                        questState.eveningChestClaimed -> {
                            Text(
                                text = "Claimed today ✓",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF58CC02)
                            )
                        }
                        questState.isNightOwlAvailable -> {
                            Text(
                                text = "Your reward chest is ready!",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF1CB0F6)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Duolingo3DButton(
                                text = "CLAIM",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                buttonColor = Color(0xFF1CB0F6),
                                bevelColor = Color(0xFF1899D6),
                                textColor = Color.White,
                                onClick = {
                                    selectedChestRarity = ChestRarity.RARE
                                    DailyQuestManager.markEveningChestClaimed()
                                    showChestDialog = true
                                }
                            )
                        }
                        else -> {
                            val nightOwlDescription = buildAnnotatedString {
                                append("Do a test between 6:00pm and 6:00am to unlock this chest.")
                            }

                            Text(
                                text = nightOwlDescription,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}
