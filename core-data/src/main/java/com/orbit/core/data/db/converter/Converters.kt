package com.orbit.core.data.db.converter

import androidx.room.TypeConverter
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.SessionStatus

class Converters {

    @TypeConverter
    fun fromPriority(priority: Priority?): String? = priority?.name

    @TypeConverter
    fun toPriority(value: String?): Priority = value?.let {
        try {
            Priority.valueOf(it)
        } catch (_: Exception) {
            Priority.MEDIUM
        }
    } ?: Priority.MEDIUM

    @TypeConverter
    fun fromSessionStatus(status: SessionStatus?): String? = status?.name

    @TypeConverter
    fun toSessionStatus(value: String?): SessionStatus = value?.let {
        try {
            SessionStatus.valueOf(it)
        } catch (_: Exception) {
            SessionStatus.STOPPED
        }
    } ?: SessionStatus.STOPPED

    @TypeConverter
    fun fromCounterAction(action: CounterAction?): String? = action?.name

    @TypeConverter
    fun toCounterAction(value: String?): CounterAction = value?.let {
        try {
            CounterAction.valueOf(it)
        } catch (_: Exception) {
            CounterAction.INCREMENT
        }
    } ?: CounterAction.INCREMENT
}
