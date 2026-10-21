package com.orbit.feature.attention

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitFilterChip
import com.orbit.core.ui.component.OrbitOutlinedButton
import com.orbit.core.ui.component.OrbitStatTile
import com.orbit.core.ui.component.OrbitTopAppBar
import com.orbit.core.ui.theme.OrbitBackground
import com.orbit.core.ui.theme.OrbitCardBorder
import com.orbit.core.ui.theme.OrbitCardDark
import com.orbit.core.ui.theme.OrbitCardElevated
import com.orbit.core.ui.theme.OrbitGreen
import com.orbit.core.ui.theme.OrbitOrange
import com.orbit.core.ui.theme.OrbitTextPrimary
import com.orbit.core.ui.theme.OrbitTextSecondary
import com.orbit.core.ui.util.isHiltAvailable
import com.orbit.feature.attention.service.ReelCounterOverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttentionScreen(
    modifier: Modifier = Modifier,
    viewModel: AttentionViewModel? = null
) {
    val context = LocalContext.current
    val effectiveViewModel: AttentionViewModel? = viewModel ?: if (isHiltAvailable(context)) {
        hiltViewModel<AttentionViewModel>()
    } else {
        null
    }

    if (effectiveViewModel == null) {
        Scaffold(
            topBar = { OrbitTopAppBar(title = "Attention & Privacy") },
            modifier = modifier
        ) { innerPadding ->
            OrbitEmptyState(
                title = "Attention Tracking",
                description = "Private app usage and manual Instagram reel tracking. All data stays local.",
                icon = Icons.Default.Visibility,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    } else {
        AttentionScreenContent(
            modifier = modifier,
            viewModel = effectiveViewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttentionScreenContent(
    modifier: Modifier = Modifier,
    viewModel: AttentionViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OrbitBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.manualIncrement() },
                containerColor = OrbitOrange,
                contentColor = OrbitBackground,
                shape = CircleShape,
                modifier = Modifier.semantics { contentDescription = "Record Reel" }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Reel")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // 1. Header with Settings Action (Screen 11 design)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reels Usage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTextPrimary
                        )
                        Text(
                            text = "Attention Tracking",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrbitTextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.openSettingsSheet() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Tracking Settings",
                            tint = OrbitTextSecondary
                        )
                    }
                }
            }

            // 2. Filter Tabs: Day, Week, Month
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AttentionTimeFilter.values().forEach { filter ->
                        OrbitFilterChip(
                            selected = state.activeTimeFilter == filter,
                            text = filter.name.lowercase().replaceFirstChar { it.uppercase() },
                            onClick = { viewModel.setTimeFilter(filter) }
                        )
                    }
                }
            }

            // 3. Large Stat Headline
            item {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "${state.todayDurationFormatted} Today",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.trendText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 4. Weekly Bar Chart (Screen 11 design)
            item {
                OrbitCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Daily Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            color = OrbitTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Chart Bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            state.weeklyDays.forEach { bar ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .fillMaxHeight(bar.heightRatio)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (bar.isToday) OrbitOrange else OrbitCardElevated)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = bar.dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (bar.isToday) OrbitOrange else OrbitTextSecondary,
                                        fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Metric Tiles (Reels watched & Total time)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OrbitStatTile(
                        value = state.todayCount.toString(),
                        label = "Reels watched",
                        icon = Icons.Default.Visibility,
                        modifier = Modifier.weight(1f)
                    )
                    OrbitStatTile(
                        value = state.todayDurationFormatted,
                        label = "Total time",
                        icon = Icons.Default.Timer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 6. Daily Goal Card
            item {
                OrbitCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Goal: Less than ${state.dailyGoalMinutes / 60} hour",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTextPrimary
                            )
                            val remaining = (state.dailyGoalMinutes - state.todayDurationMinutes).coerceAtLeast(0)
                            Text(
                                text = if (remaining > 0) "${remaining}m remaining" else "Goal reached",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (remaining > 0) OrbitGreen else OrbitOrange
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val progress = (state.todayDurationMinutes.toFloat() / state.dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progress >= 1f) OrbitOrange else OrbitGreen,
                            trackColor = OrbitCardElevated
                        )
                    }
                }
            }

            // 7. Floating Overlay Action Card (Screen 14 feature)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = OrbitCardElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OrbitCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = OrbitOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Floating Counter",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OrbitTextPrimary
                                )
                                Text(
                                    text = "Draggable overlay over Instagram",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrbitTextSecondary
                                )
                            }
                        }

                        OrbitButton(
                            text = "Launch",
                            onClick = {
                                if (state.hasOverlayPermission) {
                                    ReelCounterOverlayService.start(context)
                                } else {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }

            // 8. Honest Transparency & Privacy Banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = OrbitCardDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OrbitCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Privacy",
                            tint = OrbitOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Transparent Tracking & Data Privacy",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Instagram foreground time is measured honestly using Android UsageStatsManager when authorized. Individual Reels are recorded via the user floating counter. No accessibility scraping or invasive background sniffing is performed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OrbitTextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // Tracking Settings Sheet (Screen 12 design)
        if (state.isSettingsSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeSettingsSheet() },
                sheetState = settingsSheetState,
                containerColor = OrbitCardDark
            ) {
                TrackingSettingsBottomSheetContent(
                    isTrackingEnabled = state.isTrackingEnabled,
                    isFloatingCounterEnabled = state.isFloatingCounterEnabled,
                    isDailyGoalEnabled = state.isDailyGoalEnabled,
                    excludeShortViews = state.excludeShortViews,
                    onToggleTracking = { viewModel.toggleTracking(it) },
                    onToggleFloatingCounter = { viewModel.toggleFloatingCounter(it) },
                    onToggleDailyGoal = { viewModel.toggleDailyGoal(it) },
                    onToggleExcludeShortViews = { viewModel.toggleExcludeShortViews(it) },
                    onClose = { viewModel.closeSettingsSheet() }
                )
            }
        }
    }
}

@Composable
private fun TrackingSettingsBottomSheetContent(
    isTrackingEnabled: Boolean,
    isFloatingCounterEnabled: Boolean,
    isDailyGoalEnabled: Boolean,
    excludeShortViews: Boolean,
    onToggleTracking: (Boolean) -> Unit,
    onToggleFloatingCounter: (Boolean) -> Unit,
    onToggleDailyGoal: (Boolean) -> Unit,
    onToggleExcludeShortViews: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Reels Tracking Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitTextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle: Enable tracking
        SettingToggleRow(
            title = "Enable tracking",
            description = "Track Instagram usage sessions locally",
            checked = isTrackingEnabled,
            onCheckedChange = onToggleTracking
        )

        // Toggle: Floating counter
        SettingToggleRow(
            title = "Floating counter",
            description = "Show interactive overlay pill over Instagram",
            checked = isFloatingCounterEnabled,
            onCheckedChange = onToggleFloatingCounter
        )

        // Toggle: Show daily goal
        SettingToggleRow(
            title = "Show daily goal",
            description = "Display progress bar and alert when exceeded",
            checked = isDailyGoalEnabled,
            onCheckedChange = onToggleDailyGoal
        )

        // Toggle: Exclude short views
        SettingToggleRow(
            title = "Exclude short views (< 3 seconds)",
            description = "Filters out accidental scrolls and quick dismissals",
            checked = excludeShortViews,
            onCheckedChange = onToggleExcludeShortViews
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Options: Reset daily at
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reset daily at",
                style = MaterialTheme.typography.bodyLarge,
                color = OrbitTextPrimary
            )
            Text(
                text = "12:00 AM",
                style = MaterialTheme.typography.bodyMedium,
                color = OrbitOrange
            )
        }

        // App detection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "App detection",
                style = MaterialTheme.typography.bodyLarge,
                color = OrbitTextPrimary
            )
            Text(
                text = "Instagram only",
                style = MaterialTheme.typography.bodyMedium,
                color = OrbitTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OrbitButton(
            text = "Done",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = OrbitTextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = OrbitTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OrbitBackground,
                checkedTrackColor = OrbitOrange,
                uncheckedThumbColor = OrbitTextSecondary,
                uncheckedTrackColor = OrbitCardElevated
            )
        )
    }
}
