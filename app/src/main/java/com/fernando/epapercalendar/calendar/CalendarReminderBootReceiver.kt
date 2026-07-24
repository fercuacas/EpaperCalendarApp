package com.fernando.epapercalendar.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CalendarReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val events = CalendarRepository(context).loadEvents()
        CalendarReminderScheduler(context).scheduleAll(events)
    }
}
