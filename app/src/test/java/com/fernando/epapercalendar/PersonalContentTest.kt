package com.fernando.epapercalendar

import com.fernando.epapercalendar.personal.*
import java.time.LocalDate
import java.time.MonthDay
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PersonalContentTest {
    private fun fixture() = PersonalContentCatalog("fixture", LocalDate.of(2026, 1, 1), 7,
        listOf("one", "two", "three"), listOf(PersonalImage("a.epd", ""), PersonalImage("b.epd", "")),
        listOf("a.epd", "b.epd"), mapOf(MonthDay.of(1, 2) to "Especial", MonthDay.of(2, 29) to "Bisiesto"),
        mapOf(MonthDay.of(1, 3) to "b.epd", MonthDay.of(2, 29) to "a.epd"))

    @Test fun annualDatesPauseEachListIndependently() {
        val catalog = fixture()
        fun pick(day: String) = catalog.forDate(LocalDate.parse(day))
        assertEquals("one", pick("2026-01-01").phrase)
        assertEquals("Especial", pick("2026-01-02").phrase)
        assertEquals("two", pick("2026-01-03").phrase)
        assertEquals("three", pick("2026-01-04").phrase)
        assertEquals("Especial", pick("2027-01-02").phrase)
        assertEquals("b.epd", pick("2027-01-03").image?.name)
        assertTrue(pick("2026-01-02").phraseSpecial)
        assertFalse(pick("2026-01-02").imageSpecial)
        assertFalse(pick("2026-01-03").phraseSpecial)
        assertTrue(pick("2026-01-03").imageSpecial)
        assertEquals("Bisiesto", pick("2028-02-29").phrase)
        assertFalse(pick("2027-02-28").phraseSpecial)
        assertFalse(pick("2100-03-01").imageSpecial)
    }

    @Test fun pauseOffsetsAreReversibleAcrossYearsAndLeapCenturies() {
        val days = setOf(MonthDay.of(12, 31), MonthDay.of(1, 1), MonthDay.of(2, 29))
        assertEquals(1L, normalContentOffset(LocalDate.of(2027,1,2), LocalDate.of(2026,12,30), days))
        assertEquals(-1L, normalContentOffset(LocalDate.of(2026,12,30), LocalDate.of(2027,1,2), days))
        for (year in listOf(2024, 2027, 2100)) {
            assertEquals(1L, normalContentOffset(LocalDate.of(year,3,1), LocalDate.of(year,2,28), days))
        }
    }

    @Test fun shuffleIsDeterministicAndExhaustsEveryPhotoBeforeRepeating() {
        for (count in listOf(1, 4, 166, 512)) for (cycle in -3..3) {
            val result = (0 until count).map { shuffledImageIndex(cycle.toLong() * count + it, count, 20261026) }
            assertEquals((0 until count).toList(), result.sorted())
        }
        val catalog = fixture()
        val date = LocalDate.of(2026, 10, 26)
        assertEquals(catalog.forDate(date), catalog.forDate(date))
    }

    @Test fun packagedCatalogMatchesSharedPythonGoldenSelectionsAndContainsRealPngs() {
        val text = javaClass.getResourceAsStream("/personal_content/catalog.json")!!.bufferedReader().use { it.readText() }
        val catalog = PersonalContentCatalog.fromJson(text)
        val root = JSONObject(text)
        val checks = root.getJSONArray("checks")
        assertTrue(checks.length() > 100)
        for (index in 0 until checks.length()) {
            val expected = checks.getJSONObject(index)
            val actual = catalog.forDate(LocalDate.parse(expected.getString("date")))
            assertEquals(if (expected.isNull("phrase")) null else expected.getString("phrase"), actual.phrase)
            assertEquals(expected.getInt("phraseIndex"), actual.phraseIndex)
            assertEquals(expected.getInt("imageIndex"), actual.imageIndex)
            assertEquals(if (expected.isNull("image")) null else expected.getString("image"), actual.image?.name)
            assertEquals(expected.getBoolean("phraseSpecial"), actual.phraseSpecial)
            assertEquals(expected.getBoolean("imageSpecial"), actual.imageSpecial)
            actual.image?.let { image ->
                val png = Base64.getDecoder().decode(image.previewPng)
                assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), png.copyOfRange(0, 8))
            }
        }
        val today = LocalDate.of(2026,10,26)
        assertTrue(catalog.deviceCompatibility("""{"assetsVersion":"${catalog.assetsVersion}","today":"$today"}""", today).startsWith("Fotos y frases coinciden"))
        assertTrue(catalog.deviceCompatibility("""{"assetsVersion":"old"}""", today).contains("versiones distintas"))
        assertTrue(catalog.deviceCompatibility("""{"assetsVersion":"${catalog.assetsVersion}","today":"2000-01-01"}""", today).contains("fecha"))
        assertTrue(catalog.deviceCompatibility("{}", today).contains("no se puede verificar"))
    }

    @Test fun emptyNormalListsStillSupportSpecialDaysAndInvalidImageBindingsFail() {
        val onlySpecial = PersonalContentCatalog("special", LocalDate.of(2026,1,1), 0, emptyList(),
            listOf(PersonalImage("a.epd", "")), emptyList(), mapOf(MonthDay.of(1,1) to "Especial"),
            mapOf(MonthDay.of(1,1) to "a.epd"))
        assertEquals("Especial", onlySpecial.forDate(LocalDate.of(2026,1,1)).phrase)
        assertNotNull(onlySpecial.forDate(LocalDate.of(2026,1,1)).image)
        assertNull(onlySpecial.forDate(LocalDate.of(2026,1,2)).phrase)
        assertNull(onlySpecial.forDate(LocalDate.of(2026,1,2)).image)
        assertThrows(IllegalArgumentException::class.java) {
            PersonalContentCatalog("invalid", LocalDate.now(), 0, emptyList(), emptyList(), listOf("missing.epd"), emptyMap(), emptyMap())
        }
    }
}
