package com.fernando.epapercalendar

import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.EventCategory
import com.fernando.epapercalendar.calendar.RecurrenceRule
import com.fernando.epapercalendar.epaper.EpaperHttpClient
import com.fernando.epapercalendar.epaper.EpaperHttpException
import com.fernando.epapercalendar.epaper.EpaperPayloadBuilder
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpaperIntegrationTest {
    @Test
    fun payloadExpandsRecurrenceAndOmitsCompletedTasks() {
        val selected = LocalDate.of(2026, 9, 19)
        val recurring = event(
            id = "weekly",
            date = selected.minusYears(12),
            recurrenceRule = RecurrenceRule.WEEKLY
        )
        val multiDay = event(
            id = "trip",
            date = selected.minusDays(1),
            endDate = selected.plusDays(2),
            allDay = true
        )
        val doneTask = event(
            id = "done",
            date = selected,
            category = EventCategory.TASK,
            done = true
        )

        val result = EpaperPayloadBuilder.build(
            events = listOf(recurring, multiDay, doneTask),
            selectedDate = selected,
            zoneId = ZoneId.of("Europe/Madrid"),
            now = ZonedDateTime.parse("2026-09-19T12:00:00+02:00[Europe/Madrid]")
        )

        assertEquals(LocalDate.of(2026, 3, 1), result.rangeStart)
        assertEquals(LocalDate.of(2027, 3, 31), result.rangeEnd)
        assertTrue(result.occurrenceCount > 20)
        assertTrue(result.json.contains("\"version\":2"))
        assertTrue(result.json.contains("trip@2026-09-18"))
        assertFalse(result.json.contains("done@"))
    }

    @Test
    fun httpClientSupportsStatusProvisionAndCalendar() {
        val status = serveOnce(200, "{\"ok\":true}") { host -> EpaperHttpClient().getStatus(host) }
        assertEquals(200, status.statusCode)

        val provision = serveOnce(202, "{\"ok\":true}") { host ->
            EpaperHttpClient().configureWifi(host, "Mi red", "secreto")
        }
        assertEquals(202, provision.statusCode)

        val calendar = serveOnce(202, "{\"accepted\":true}") { host ->
            EpaperHttpClient().sendCalendar(host, "{\"version\":2}")
        }
        assertTrue(calendar.body.contains("accepted"))
    }

    @Test
    fun httpClientSurfacesErrorsAndTimeouts() {
        assertThrows(EpaperHttpException::class.java) {
            serveOnce(422, "{\"error\":\"bad payload\"}") { host -> EpaperHttpClient().sendCalendar(host, "{}") }
        }

        val server = ServerSocket(0)
        val worker = thread {
            server.accept().use { Thread.sleep(250) }
            server.close()
        }
        assertThrows(SocketTimeoutException::class.java) {
            EpaperHttpClient(connectTimeoutMillis = 100, readTimeoutMillis = 50)
                .getStatus("127.0.0.1:${server.localPort}")
        }
        worker.join()
    }

    private fun event(
        id: String,
        date: LocalDate,
        endDate: LocalDate = date,
        category: EventCategory = EventCategory.WORK,
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        allDay: Boolean = false,
        done: Boolean = false
    ) = CalendarEvent(
        id = id,
        title = id,
        location = "",
        date = date,
        endDate = endDate,
        startMinutes = 9 * 60,
        endMinutes = 10 * 60,
        category = category,
        recurrenceRule = recurrenceRule,
        allDay = allDay,
        done = done
    )

    private fun <T> serveOnce(status: Int, responseBody: String, request: (String) -> T): T {
        val server = ServerSocket(0)
        val worker = thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toInt()
                    }
                }
                repeat(contentLength) { reader.read() }
                val reason = if (status in 200..299) "Accepted" else "Unprocessable Entity"
                val bytes = responseBody.toByteArray()
                socket.getOutputStream().use { output ->
                    output.write("HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(bytes)
                }
            }
            server.close()
        }
        return request("127.0.0.1:${server.localPort}").also { worker.join() }
    }
}
