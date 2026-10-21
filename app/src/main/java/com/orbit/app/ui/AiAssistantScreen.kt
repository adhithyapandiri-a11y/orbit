package com.orbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitFilterChip
import com.orbit.core.ui.component.OrbitTextField
import com.orbit.core.ui.theme.OrbitBackground
import com.orbit.core.ui.theme.OrbitCardBorder
import com.orbit.core.ui.theme.OrbitCardDark
import com.orbit.core.ui.theme.OrbitCardElevated
import com.orbit.core.ui.theme.OrbitGreen
import com.orbit.core.ui.theme.OrbitOrange
import com.orbit.core.ui.theme.OrbitTextPrimary
import com.orbit.core.ui.theme.OrbitTextSecondary
import com.orbit.core.ui.component.OrbitTopAppBar
import com.orbit.core.ui.util.isHiltAvailable
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    modifier: Modifier = Modifier,
    viewModel: AiAssistantViewModel? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val effectiveViewModel: AiAssistantViewModel? = viewModel ?: if (isHiltAvailable(context)) {
        hiltViewModel<AiAssistantViewModel>()
    } else {
        null
    }

    if (effectiveViewModel == null) {
        Scaffold(
            topBar = { OrbitTopAppBar(title = "AI Assistant") },
            modifier = modifier
        ) { innerPadding ->
            Text(
                text = "AI Assistant",
                modifier = Modifier.padding(innerPadding)
            )
        }
        return
    }

    val viewModel = effectiveViewModel
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OrbitBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Header (Screen 7 design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OrbitTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTextPrimary
                        )
                        Text(
                            text = state.activeProviderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = OrbitOrange
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OrbitCardDark)
                        .border(1.dp, OrbitCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = OrbitOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Suggestion pills (Screen 7 design)
            val suggestions = listOf("Add task", "Summarize tasks", "Save note", "Plan my day")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { suggestion ->
                    OrbitFilterChip(
                        selected = false,
                        text = suggestion,
                        onClick = { viewModel.sendMessage(suggestion) },
                        icon = Icons.Default.AutoAwesome
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Chat Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        onExecuteAction = { action ->
                            viewModel.executeAction(msg.id, action)
                        }
                    )
                }

                if (state.isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = OrbitOrange,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Assistant is processing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = OrbitTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Bottom Input Bar (Screen 7 design)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbitTextField(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    placeholder = "Ask anything (e.g. Add meeting tomorrow 3pm !high)...",
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (state.inputText.isNotBlank()) OrbitOrange else OrbitCardElevated)
                                .clickable(enabled = state.inputText.isNotBlank()) {
                                    viewModel.sendMessage()
                                }
                                .semantics { contentDescription = "Send Message" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (state.inputText.isNotBlank()) OrbitBackground else OrbitTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onExecuteAction: (ProposedAction) -> Unit
) {
    if (message.isFromUser) {
        // User bubble: Right aligned, accent orange container
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(OrbitOrange)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrbitBackground,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        // Assistant bubble: Left aligned, dark elevated card
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            OrbitCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitTextPrimary
                    )

                    // Structured Action Proposal (Screen 18 design)
                    if (message.proposedAction != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OrbitCardElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, OrbitCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                val actionLabel = when (message.proposedAction) {
                                    is ProposedAction.CreateTask -> "Add to Tasks"
                                    is ProposedAction.CreateNote -> "Save to Notes"
                                }
                                Text(
                                    text = actionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OrbitOrange,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                if (message.actionExecuted) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = OrbitGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Action confirmed & executed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OrbitGreen,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    OrbitButton(
                                        text = "Confirm & Execute",
                                        onClick = { onExecuteAction(message.proposedAction) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
