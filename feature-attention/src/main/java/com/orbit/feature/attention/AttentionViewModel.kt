package com.orbit.feature.attention

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.DailyReelStatistic
import com.orbit.core.domain.repository.PreferencesRepository
import com.orbit.core.domain.repository.ReelCounterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class AttentionTimeFilter {
    DAY,
    WEEK,
    MONTH
}

data class AttentionUiState(
    val activeTimeFilter: AttentionTimeFilter = AttentionTimeFilter.WEEK,
    val todayStat: DailyReelStatistic? = null,
    val todayCount: Int = 0,
    val todayDurationMinutes: Long = 0,
    val todayDurationFormatted: String = "0m",
    val trendText: String = "↓ 26% vs last week",
    val weeklyDays: List<DayUsageBar> = emptyList(),
    val hasUsagePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isTrackingEnabled: Boolean = true,
    val isFloatingCounterEnabled: Boolean = false,
    val isDailyGoalEnabled: Boolean = true,
    val dailyGoalMinutes: Int = 60,
    val excludeShortViews: Boolean = true,
    val isSettingsSheetOpen: Boolean = false
)

data class DayUsageBar(
    val dayLabel: String,
    val durationMinutes: Long,
    val isToday: Boolean,
    val heightRatio: Float
)

@HiltViewModel
class AttentionViewModel @Inject constructor(
    private val reelCounterRepository: ReelCounterRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttentionUiState())
    private val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val uiState: StateFlow<AttentionUiState> = combine(
        reelCounterRepository.getDailyStatistic(todayIso),
        reelCounterRepository.getDailyStatistics(7),
        preferencesRepository.getUserPreferences(),
        _uiState
    ) { todayStat, last7Days, userPrefs, state ->
        val count = todayStat?.manualCount ?: 0
        val durationMs = (todayStat?.activeDurationMillis ?: 0L) + (todayStat?.instagramForegroundDurationMillis ?: 0L)
        val minutes = durationMs / (1000 * 60)

        val formattedDuration = if (minutes >= 60) {
            "${minutes / 60}h ${minutes % 60}m"
        } else {
            "${minutes}m"
        }

        // Build 7-day bar chart data
        val today = LocalDate.now()
        val daysList = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val dayName = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

            val stat = last7Days.firstOrNull { it.localDate == dateStr }
            val statMin = if (stat != null) {
                (stat.activeDurationMillis + stat.instagramForegroundDurationMillis) / (1000 * 60)
            } else 0L

            dayName to statMin
        }

        val maxMinutes = daysList.maxOfOrNull { it.second }?.coerceAtLeast(60L) ?: 60L
        val dayUsageBars = daysList.mapIndexed { index, (name, min) ->
            DayUsageBar(
                dayLabel = name,
                durationMinutes = min,
                isToday = (index == daysList.lastIndex),
                heightRatio = (min.toFloat() / maxMinutes.toFloat()).coerceIn(0.1f, 1.0f)
            )
        }

        state.copy(
            todayStat = todayStat,
            todayCount = count,
            todayDurationMinutes = minutes,
            todayDurationFormatted = formattedDuration,
            weeklyDays = dayUsageBars,
            isTrackingEnabled = userPrefs.featureFlags.instagramAwarenessEnabled,
            isFloatingCounterEnabled = userPrefs.featureFlags.overlayWidgetEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AttentionUiState()
    )

    fun setTimeFilter(filter: AttentionTimeFilter) {
        _uiState.update { it.copy(activeTimeFilter = filter) }
    }

    fun manualIncrement() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val allSessions = reelCounterRepository.getAllSessionsDirect()
            val activeSession = allSessions.firstOrNull {
                it.status == com.orbit.core.domain.model.SessionStatus.ACTIVE && it.localDate == todayIso
            } ?: reelCounterRepository.startSession(todayIso, now)
            reelCounterRepository.recordEvent(activeSession.id, CounterAction.INCREMENT, now)
        }
    }

    fun openSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetOpen = true) }
    }

    fun closeSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetOpen = false) }
    }

    fun toggleTracking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateFeatureFlags {
                it.copy(instagramAwarenessEnabled = enabled)
            }
        }
    }

    fun toggleFloatingCounter(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateFeatureFlags {
                it.copy(overlayWidgetEnabled = enabled)
            }
        }
    }

    fun toggleDailyGoal(enabled: Boolean) {
        _uiState.update { it.copy(isDailyGoalEnabled = enabled) }
    }

    fun toggleExcludeShortViews(enabled: Boolean) {
        _uiState.update { it.copy(excludeShortViews = enabled) }
    }

    fun checkPermissions(context: Context) {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        val hasUsage = mode == AppOpsManager.MODE_ALLOWED

        _uiState.update {
            it.copy(
                hasOverlayPermission = hasOverlay,
                hasUsagePermission = hasUsage
            )
        }
    }
}
