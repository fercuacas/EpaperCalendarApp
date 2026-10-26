package com.fernando.epapercalendar

import com.fernando.epapercalendar.calendar.*
import com.fernando.epapercalendar.calendar.device.*
import com.fernando.epapercalendar.epaper.EpaperPayloadException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DeviceCalendarTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val date = LocalDate.of(2026, 10, 23)
    private val info = DeviceCalendarInfo(DeviceCalendarIdentity(7, "cuenta de prueba", "com.google"), "Trabajo", 0xff004499.toInt())
    private fun millis(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private fun occurrence(id: Long = 10, title: String = "Reunión") = DeviceOccurrence(id,
        millis("2026-10-23T09:00:00+02:00"), millis("2026-10-23T10:00:00+02:00"), title)
    private fun event(id: Long = 10, title: String = "Reunión") = occurrence(id, title).toEvent(info, EventCategory.WORK, madrid)!!

    @Test fun timedEventsUsePhoneTimezoneAndPreserveSource() {
        val event = occurrence().toEvent(info, EventCategory.WORK, ZoneId.of("America/New_York"))!!
        assertEquals(date, event.date)
        assertEquals(3 * 60, event.startMinutes)
        assertTrue(event.isReadOnly)
        assertEquals(RecurrenceRule.NONE, event.recurrenceRule)
        assertEquals(ReminderOption.NONE, event.reminder)
        assertEquals(info.identity, event.copy(title = "Otro").deviceSource?.calendar)
        assertEquals(event.deviceSource, event.occurrencesOverlapping(date, date).single().asCalendarEvent().deviceSource)
    }

    @Test fun allDayDatesStayUtcAcrossPhoneTimezones() {
        val raw = DeviceOccurrence(1, millis("2026-10-23T00:00:00Z"), millis("2026-10-26T00:00:00Z"), "Viaje", allDay = true)
        listOf("Pacific/Honolulu", "Asia/Tokyo", "Europe/Madrid").forEach { zone ->
            val event = raw.toEvent(info, EventCategory.PERSONAL, ZoneId.of(zone))!!
            assertEquals(date, event.date)
            assertEquals(date.plusDays(2), event.endDate)
            assertEquals(0, event.startMinutes)
            assertEquals(1439, event.endMinutes)
        }
    }

    @Test fun exclusiveMidnightDoesNotAddAnotherDay() {
        val raw = DeviceOccurrence(1, millis("2026-10-23T21:00:00+02:00"), millis("2026-10-24T00:00:00+02:00"), "Cena")
        val event = raw.toEvent(info, EventCategory.PERSONAL, madrid)!!
        assertEquals(date, event.endDate)
        assertEquals(1439, event.endMinutes)
        val multi = raw.copy(endMillis = millis("2026-10-26T00:00:00+01:00")).toEvent(info, EventCategory.PERSONAL, madrid)!!
        assertEquals(date.plusDays(2), multi.endDate)
    }

    @Test fun dstAndCancelledOrDeletedInstances() {
        val raw = DeviceOccurrence(1, millis("2026-03-29T00:30:00Z"), millis("2026-03-29T01:30:00Z"), "Cambio horario")
        val event = raw.toEvent(info, EventCategory.PERSONAL, madrid)!!
        assertEquals(90, event.startMinutes)
        assertEquals(210, event.endMinutes)
        val autumn = raw.copy(beginMillis = millis("2026-10-25T00:15:00Z"), endMillis = millis("2026-10-25T02:30:00Z"))
            .toEvent(info, EventCategory.PERSONAL, madrid)!!
        assertEquals(135, autumn.startMinutes)
        assertEquals(210, autumn.endMinutes)
        assertNull(raw.copy(cancelled = true).toEvent(info, EventCategory.PERSONAL, madrid))
        assertNull(raw.copy(deleted = true).toEvent(info, EventCategory.PERSONAL, madrid))
        assertNull(raw.copy(endMillis = raw.beginMillis - 1).toEvent(info, EventCategory.PERSONAL, madrid))
    }

    @Test fun rangeMatchesThirteenMonthPayloadWindow() {
        assertEquals(DeviceCalendarRange(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 4, 30)), DeviceCalendarRange.around(date))
        assertEquals(LocalDate.of(2024, 2, 29), DeviceCalendarRange.around(LocalDate.of(2023, 8, 31)).endInclusive)
    }

    @Test fun cacheRoundTripIsVersionedSeparateAndReadOnly() {
        val original = DeviceCalendarData(listOf(DeviceCalendarSelection(info.identity, EventCategory.WORK)),
            DeviceImportSnapshot(DeviceCalendarRange.around(date), madrid.id, 123L, listOf(event())))
        assertEquals(original, DeviceCalendarCodec.decode(DeviceCalendarCodec.encode(original)))
        assertEquals(DeviceCalendarData(), DeviceCalendarCodec.decode("{\"version\":99}"))
        assertEquals(DeviceCalendarData(), DeviceCalendarCodec.decode("broken"))
        val corruptSnapshot = "{\"version\":1,\"selections\":[{\"calendarId\":7,\"account\":\"cuenta de prueba\",\"accountType\":\"com.google\",\"category\":\"WORK\"}],\"snapshot\":{}}"
        assertEquals(original.selections, DeviceCalendarCodec.decode(corruptSnapshot).selections)
        assertNull(DeviceCalendarCodec.decode(corruptSnapshot).snapshot)
        assertNull(DeviceCalendarCodec.decode(DeviceCalendarCodec.encode(original.copy(snapshot = null))).snapshot)
    }

    @Test fun updatesReplaceDuplicatesChangesAndDeletionsWithoutTouchingLocalEvents() = runBlocking {
        val fixture = Fixture()
        val local = localEvent()
        val state = CalendarState(MemoryCalendarStore(listOf(local)))
        val controller = fixture.controller(state::replaceImportedEvents, state::setImportsReadyForPayload)
        controller.selectDate(date)
        controller.refresh()
        controller.selectCalendar(info.identity, EventCategory.WORK)
        fixture.reader.events = listOf(event(), event())
        assertTrue(controller.refresh())
        assertEquals(2, state.events.size)
        assertTrue(controller.refresh())
        assertEquals(2, state.events.size)
        fixture.reader.events = listOf(event(title = "Modificado"))
        controller.refresh()
        assertEquals("Modificado", state.events.single { it.isReadOnly }.title)
        fixture.reader.events = emptyList()
        controller.refresh()
        assertEquals(listOf(local), state.events)
    }

    @Test fun revokedPermissionPurgesCacheButRetainsTechnicalSelection() = runBlocking {
        val fixture = Fixture()
        val controller = fixture.ready()
        fixture.reader.granted = false
        assertFalse(controller.refresh())
        assertNull(controller.snapshot)
        assertNull(fixture.storage.data.snapshot)
        assertEquals(1, fixture.storage.data.selections.size)
        assertTrue(controller.calendars.isEmpty())
        fixture.reader.granted = true
        assertTrue(controller.refresh())
        assertEquals(1, controller.snapshot!!.events.size)
        controller.disconnect()
        assertEquals(DeviceCalendarData(), fixture.storage.data)
    }

    @Test fun failedRefreshKeepsLastCopyAndBlocksPreflight() = runBlocking {
        val fixture = Fixture()
        val state = CalendarState(MemoryCalendarStore())
        state.selectDate(date)
        val controller = fixture.controller(state::replaceImportedEvents, state::setImportsReadyForPayload)
        controller.selectDate(date)
        controller.refresh()
        controller.selectCalendar(info.identity, EventCategory.WORK)
        controller.refresh()
        val previous = controller.snapshot
        fixture.reader.fail = true
        assertFalse(controller.refresh())
        assertEquals(previous, controller.snapshot)
        var rejected = false
        try { controller.prepareEpaperPayload(state) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertThrows(IllegalStateException::class.java) { state.buildEpaperPayload() }
        Unit
    }

    @Test fun removedCalendarOrReusedIdDoesNotAttachToAnotherAccount() = runBlocking {
        val fixture = Fixture()
        val controller = fixture.ready()
        fixture.reader.available = listOf(info.copy(identity = info.identity.copy(accountName = "Otra cuenta")))
        assertTrue(controller.refresh())
        assertTrue(controller.selections.isEmpty())
        assertNull(controller.snapshot)
    }

    @Test fun deselectionInvalidatesInFlightResultsAndRemovesEventsImmediately() = runBlocking {
        val fixture = Fixture()
        var displayed = emptyList<CalendarEvent>()
        val controller = fixture.controller({ displayed = it })
        controller.selectDate(date)
        controller.selectCalendar(info.identity, EventCategory.WORK)
        controller.refresh()
        assertEquals(1, displayed.size)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.reader.beforeRead = { entered.complete(Unit); release.await() }
        val reading = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(controller.refresh()) }
        entered.await()
        val deselect = launch(start = CoroutineStart.UNDISPATCHED) { controller.selectCalendar(info.identity, null) }
        assertTrue(displayed.isEmpty())
        release.complete(Unit)
        reading.join(); deselect.join()
        assertTrue(displayed.isEmpty())
        assertTrue(fixture.storage.data.selections.isEmpty())
    }

    @Test fun rangeChangeDiscardsOldQueryAndReadsNewRange() = runBlocking {
        val fixture = Fixture()
        val controller = fixture.ready()
        val previous = controller.snapshot
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        fixture.reader.beforeRead = { entered.complete(Unit); release.await() }
        val reading = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(controller.refresh()) }
        entered.await()
        controller.selectDate(date.plusMonths(2))
        release.complete(Unit); reading.join()
        assertEquals(previous, controller.snapshot)
        fixture.reader.beforeRead = {}
        assertTrue(controller.refresh())
        assertEquals(DeviceCalendarRange.around(date.plusMonths(2)), controller.snapshot!!.range)
    }

    @Test fun importedMutationsAreRejectedAndLocalPersistenceStaysSeparate() {
        val local = localEvent()
        val store = MemoryCalendarStore(listOf(local))
        val state = CalendarState(store)
        state.selectDate(date)
        state.replaceImportedEvents(listOf(event()))
        assertFalse(state.saveEvent(event().toDraft()))
        assertFalse(state.saveRecurringEdit(event().toDraft(), RecurrenceEditMode.SINGLE_OCCURRENCE))
        state.deleteEvent(event().id)
        state.toggleDone(event().id)
        assertEquals(2, state.events.size)
        assertFalse(state.events.single { it.isReadOnly }.done)
        assertEquals(listOf(local), store.events)
        assertTrue(state.saveEvent(local.toDraft().copy(title = "Local editado")))
        assertEquals(1, store.events.size)
        assertFalse(store.events.single().isReadOnly)
        val tutorial = CalendarState(store, initialEvents = emptyList(), persistChanges = false)
        tutorial.replaceImportedEvents(listOf(event()))
        assertTrue(tutorial.events.isEmpty())
    }

    @Test fun combinedPayloadContainsExpandedInstancesWithoutSourceMetadataOrVisualFilters() = runBlocking {
        val fixture = Fixture()
        val state = CalendarState(MemoryCalendarStore(listOf(localEvent())))
        state.selectDate(date)
        state.updateCategoryFilter(EventCategory.FITNESS)
        state.selectSection(AppSection.WEEK)
        val controller = fixture.controller(state::replaceImportedEvents, state::setImportsReadyForPayload)
        controller.selectCalendar(info.identity, EventCategory.WORK)
        fixture.reader.events = listOf(event(), event(11), event(12))
        val result = controller.prepareEpaperPayload(state)
        assertEquals(4, result.occurrenceCount)
        assertTrue(result.json.contains("device:7:10:"))
        assertFalse(result.json.contains("deviceSource"))
        fixture.reader.events = (1L..769L).map { event(it) }
        var rejected = false
        try { controller.prepareEpaperPayload(state) } catch (_: EpaperPayloadException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun categoryChangesApplyImmediatelyAndTasksAreNotAllowed() = runBlocking {
        val fixture = Fixture()
        val controller = fixture.ready()
        controller.selectCalendar(info.identity, EventCategory.FITNESS)
        assertEquals(EventCategory.FITNESS, controller.snapshot!!.events.single().category)
        var rejected = false
        try { controller.selectCalendar(info.identity, EventCategory.TASK) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun localOnlyPreflightNeedsNoCalendarPermissionAndByteLimitNeverTruncates() = runBlocking {
        val fixture = Fixture()
        fixture.reader.granted = false
        val state = CalendarState(MemoryCalendarStore(listOf(localEvent())))
        state.selectDate(date)
        val controller = fixture.controller(state::replaceImportedEvents, state::setImportsReadyForPayload)
        assertEquals(1, controller.prepareEpaperPayload(state).occurrenceCount)
        fixture.reader.granted = true
        controller.selectCalendar(info.identity, EventCategory.PERSONAL)
        fixture.reader.events = (1L..200L).map { event(it, "x".repeat(600)) }
        var rejected = false
        try { controller.prepareEpaperPayload(state) } catch (_: EpaperPayloadException) { rejected = true }
        assertTrue(rejected)
        assertEquals(200, controller.snapshot!!.events.size)
    }

    @Test fun launchWithoutPermissionRemovesPersistedCacheWithoutLoadingDemo() = runBlocking {
        val fixture = Fixture()
        fixture.storage.data = DeviceCalendarData(listOf(DeviceCalendarSelection(info.identity)),
            DeviceImportSnapshot(DeviceCalendarRange.around(date), madrid.id, 1, listOf(event())))
        fixture.reader.granted = false
        var displayed = listOf(event())
        val controller = fixture.controller({ displayed = it })
        controller.initialize()
        assertTrue(displayed.isEmpty())
        assertNull(controller.snapshot)
        assertNull(fixture.storage.data.snapshot)
        assertEquals(1, controller.selections.size)
        assertFalse(controller.readyForPayload)
    }

    private fun localEvent() = CalendarEvent("local", "Local", "", date, startMinutes = 600,
        endMinutes = 660, category = EventCategory.PERSONAL, updatedAt = 0)

    private inner class Fixture {
        val reader = FakeReader()
        val storage = MemoryDeviceStorage()
        fun controller(onEvents: (List<CalendarEvent>) -> Unit = {}, onReady: (Boolean) -> Unit = {}) =
            DeviceCalendarController(reader, storage, onEvents, onReady, { madrid }, { 123L })
        suspend fun ready(): DeviceCalendarController = controller().also {
            it.selectDate(date); it.selectCalendar(info.identity, EventCategory.WORK); assertTrue(it.refresh())
        }
    }

    private inner class FakeReader : DeviceCalendarReader {
        var granted = true
        var fail = false
        var available = listOf(info)
        var events = listOf(event())
        var beforeRead: suspend () -> Unit = {}
        override fun hasPermission() = granted
        override suspend fun calendars(): List<DeviceCalendarInfo> {
            if (!granted) throw SecurityException()
            if (fail) error("Provider unavailable")
            return available
        }
        override suspend fun occurrences(calendars: List<DeviceCalendarInfo>, selections: List<DeviceCalendarSelection>,
            range: DeviceCalendarRange, zoneId: ZoneId): List<CalendarEvent> {
            beforeRead()
            return events
        }
    }
}

internal class MemoryDeviceStorage : DeviceCalendarStorage {
    var data = DeviceCalendarData()
    override suspend fun load() = data
    override suspend fun save(data: DeviceCalendarData) { this.data = data }
}

internal class MemoryCalendarStore(var events: List<CalendarEvent> = emptyList()) : CalendarStore {
    override fun loadEvents() = events
    override fun saveEvents(events: List<CalendarEvent>) { this.events = events }
    override fun loadSection() = AppSection.AGENDA
    override fun saveSection(section: AppSection) = Unit
    override fun loadCategoryFilter(): EventCategory? = null
    override fun saveCategoryFilter(category: EventCategory?) = Unit
    override fun loadDateFilter() = DateFilter.ALL
    override fun saveDateFilter(filter: DateFilter) = Unit
}
