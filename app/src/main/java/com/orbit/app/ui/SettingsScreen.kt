package com.orbit.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitConfirmationDialog
import com.orbit.core.ui.component.OrbitErrorBanner
import com.orbit.core.ui.component.OrbitLoadingIndicator
import com.orbit.core.ui.component.OrbitOutlinedButton
import com.orbit.core.ui.component.OrbitTopAppBar
import com.orbit.core.ui.theme.LocalOrbitSpacing

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalOrbitSpacing.current

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    Scaffold(
        topBar = { OrbitTopAppBar(title = "Settings & Privacy") },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = spacing.medium)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(spacing.small))

            // Status message
            if (uiState.statusMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small)
                ) {
                    Text(
                        text = uiState.statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(spacing.medium)
                    )
                }
            }

            // Error banner
            if (uiState.errorMessage != null) {
                OrbitErrorBanner(
                    errorMessage = uiState.errorMessage ?: "",
                    onDismiss = { viewModel.clearStatus() }
                )
                Spacer(modifier = Modifier.height(spacing.small))
            }

            // Privacy Manifesto Card
            OrbitCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Text(
                        text = "100% Local-First & Private",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        text = "Orbit never sends your tasks, calendar events, URLs, video notes, or counter statistics to the cloud or third parties. No account required.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Feature Flags Section
            Text(
                text = "Feature Controls",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(spacing.small))

            OrbitCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    FeatureToggleItem(
                        title = "AI-assisted Task Extraction",
                        subtitle = "Offline extraction. Always shows review screen before saving.",
                        checked = uiState.preferences.featureFlags.aiExtractionEnabled,
                        onCheckedChange = { viewModel.toggleAiExtraction(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))
                    FeatureToggleItem(
                        title = "Instagram Awareness",
                        subtitle = "Private Usage Access to show reel counter only when Instagram is active.",
                        checked = uiState.preferences.featureFlags.instagramAwarenessEnabled,
                        onCheckedChange = { viewModel.toggleInstagramAwareness(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))
                    FeatureToggleItem(
                        title = "Floating Reel Counter Overlay",
                        subtitle = "Draggable counter over Instagram. Started only by user action.",
                        checked = uiState.preferences.featureFlags.overlayWidgetEnabled,
                        onCheckedChange = { viewModel.toggleOverlayWidget(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))
                    FeatureToggleItem(
                        title = "Calendar Context",
                        subtitle = "Read-only agenda timeline. Calendar permission requested on demand.",
                        checked = uiState.preferences.featureFlags.calendarIntegrationEnabled,
                        onCheckedChange = { viewModel.toggleCalendarIntegration(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Backup & Data Sovereignty Section
            Text(
                text = "Data Sovereignty",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(spacing.small))

            OrbitCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Text(
                        text = "Export, restore, or permanently erase your data anytime.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OrbitButton(
                            text = "Export Data",
                            onClick = { viewModel.exportData() },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isBusy
                        )
                        Spacer(modifier = Modifier.padding(horizontal = spacing.extraSmall))
                        OrbitOutlinedButton(
                            text = "Restore Data",
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isBusy
                        )
                    }

                    if (uiState.exportJsonPreview != null) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Text(
                            text = "Exported JSON (with SHA-256 checksum):",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(spacing.extraSmall))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.exportJsonPreview ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8,
                                modifier = Modifier.padding(spacing.small)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))
                    OrbitButton(
                        text = "Erase All Data Permanently",
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isBusy
                    )
                }
            }

            if (uiState.isBusy) {
                Spacer(modifier = Modifier.height(spacing.medium))
                OrbitLoadingIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }
    }

    // Confirmation dialog for permanent delete
    if (showDeleteConfirmDialog) {
        OrbitConfirmationDialog(
            title = "Delete All Data?",
            message = "This will permanently wipe all tasks, calendar notes, video items, and counter history from this device. This action cannot be undone.",
            confirmButtonText = "Delete Everything",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirmDialog = false
                viewModel.eraseAllData()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Restore From JSON") },
            text = {
                Column {
                    Text("Paste verified Orbit backup JSON below. The checksum and schema will be validated prior to restoring.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        placeholder = { Text("Paste JSON here...") }
                    )
                }
            },
            confirmButton = {
                OrbitButton(
                    text = "Verify & Restore",
                    onClick = {
                        val text = importJsonText
                        showImportDialog = false
                        importJsonText = ""
                        viewModel.importData(text)
                    },
                    enabled = importJsonText.isNotBlank()
                )
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FeatureToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = LocalOrbitSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
