package com.fernando.epapercalendar.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId
import java.time.Instant

class CalendarReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll(events: List<CalendarEvent>) {
        events.forEach(::schedule)
    }

    fun schedule(event: CalendarEvent) {
        if (event.isReadOnly) return
        cancel(event.id)
        val minutesBefore = event.reminder.minutesBefore ?: return
        val triggerAt = nextTriggerMillis(event, minutesBefore) ?: return
        val intent = Intent(context, CalendarReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_EVENT_ID, event.id)
            putExtra(EXTRA_TITLE, event.title)
            putExtra(EXTRA_TIME, if (event.allDayLike) "Todo el día" else event.startLabel)
        }
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            PendingIntent.getBroadcast(
                context,
                event.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    fun cancel(eventId: String) {
        val intent = Intent(context, CalendarReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                eventId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun nextTriggerMillis(event: CalendarEvent, minutesBefore: Int): Long? {
        return nextReminderTriggerMillis(event, minutesBefore, System.currentTimeMillis(), ZoneId.systemDefault())
    }

    companion object {
        const val ACTION_REMINDER = "com.fernando.epapercalendar.ACTION_REMINDER"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TIME = "extra_time"
    }
}

internal fun nextReminderTriggerMillis(event: CalendarEvent, minutesBefore: Int, nowMillis: Long, zoneId: ZoneId): Long? {
    if (event.isReadOnly) return null
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return event.occurrencesOverlapping(today.minusDays(1), today.plusYears(2))
        .asSequence()
        .map { occurrence ->
            val startDateTime = if (event.allDayLike) occurrence.startDate.atStartOfDay()
                else occurrence.startDate.atStartOfDay().plusMinutes(event.startMinutes.toLong())
            startDateTime.minusMinutes(minutesBefore.toLong()).atZone(zoneId).toInstant().toEpochMilli()
        }
        .firstOrNull { it > nowMillis }
}
