package com.fernando.epapercalendar

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.fernando.epapercalendar.calendar.device.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Read-only smoke test of the actual provider URI, projection and permissions. No events are inserted. */
@RunWith(AndroidJUnit4::class)
class DeviceCalendarProviderInstrumentedTest {
    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun realProviderAcceptsProjectionAndRangeWithoutWritingEvents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR)
        try {
            val reader = AndroidDeviceCalendarReader(context)
            assertTrue(reader.hasPermission())
            reader.calendars()
            // A nonexistent ID exercises the real Instances projection without reading private events.
            val calendar = DeviceCalendarInfo(DeviceCalendarIdentity(Long.MAX_VALUE, "test", "test"), "Test", 0)
            assertTrue(reader.occurrences(listOf(calendar), listOf(DeviceCalendarSelection(calendar.identity)),
                DeviceCalendarRange.around(LocalDate.of(2026, 10, 23)), ZoneId.of("Europe/Madrid")).isEmpty())
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }
}
