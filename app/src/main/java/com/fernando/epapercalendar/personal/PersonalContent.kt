package com.fernando.epapercalendar.personal

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** Same integer-only schedule as Ink_calendar32/PersonalContent.cpp. No network or preferences. */
internal data class PersonalImage(val name: String, val previewPng: String)

internal data class DailyPersonalContent(
    val date: LocalDate,
    val phrase: String?,
    val phraseIndex: Int,
    val image: PersonalImage?,
    val imageIndex: Int,
    val phraseSpecial: Boolean,
    val imageSpecial: Boolean
)

internal class PersonalContentCatalog(
    val assetsVersion: String,
    val anchorDate: LocalDate,
    private val imageSeed: Long,
    private val phrases: List<String>,
    private val images: List<PersonalImage>,
    private val imagePool: List<String>,
    private val specialPhrases: Map<MonthDay, String>,
    private val specialImages: Map<MonthDay, String>
) {
    init {
        require(imageSeed in 0..0xffffffffL)
        require(images.size <= 512 && phrases.size <= 256)
        require(images.map { it.name }.distinct().size == images.size)
        require(imagePool.distinct().size == imagePool.size)
        val names = images.map { it.name }.toSet()
        require(imagePool.all { it in names } && specialImages.values.all { it in names })
    }

    fun forDate(date: LocalDate): DailyPersonalContent {
        val annual = MonthDay.from(date)
        val phraseSlot = normalContentOffset(date, anchorDate, specialPhrases.keys)
        val phraseIndex = if (phrases.isEmpty()) 0 else Math.floorMod(phraseSlot, phrases.size.toLong()).toInt()
        val normalImage = if (imagePool.isEmpty()) null else imagePool[shuffledImageIndex(
            normalContentOffset(date, anchorDate, specialImages.keys), imagePool.size, imageSeed)]
        val name = specialImages[annual] ?: normalImage
        val imageIndex = images.indexOfFirst { it.name == name }.coerceAtLeast(0)
        return DailyPersonalContent(date, specialPhrases[annual] ?: phrases.getOrNull(phraseIndex),
            phraseIndex, images.firstOrNull { it.name == name }, imageIndex,
            annual in specialPhrases, annual in specialImages)
    }

    /** Diagnostics only. A successful calendar POST doesn't update firmware-bundled pictures. */
    fun deviceCompatibility(status: String, today: LocalDate = LocalDate.now()): String {
        val device = JSONObject(status)
        val version = device.optString("assetsVersion")
        if (version.isBlank()) return "El firmware no informa de su paquete de fotos; no se puede verificar la coincidencia."
        if (version != assetsVersion) return "Las fotos/frases de la app y del e-paper son de versiones distintas. Actualiza ambos desde el mismo catálogo."
        if (device.optString("today") != today.toString()) return "El paquete de fotos coincide, pero la fecha del e-paper es distinta; sincroniza su reloj."
        return "Fotos y frases coinciden con el e-paper."
    }

    companion object {
        const val ASSET_PATH = "personal_content/catalog.json"

        fun fromJson(text: String): PersonalContentCatalog {
            val root = JSONObject(text)
            require(root.getInt("version") == 1) { "Versión de contenido personal no compatible" }
            fun strings(key: String) = root.getJSONArray(key).let { array -> List(array.length()) { array.getString(it) } }
            fun specials(key: String, value: String): Map<MonthDay, String> {
                val array = root.getJSONArray(key)
                val entries = List(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    MonthDay.parse("--${item.getString("date")}") to item.getString(value)
                }
                require(entries.map { it.first }.distinct().size == entries.size)
                return entries.toMap()
            }
            val images = root.getJSONArray("images")
            return PersonalContentCatalog(root.getString("assetsVersion"), LocalDate.parse(root.getString("anchorDate")),
                root.getLong("imageSeed"), strings("phrases"), List(images.length()) {
                    val item = images.getJSONObject(it)
                    PersonalImage(item.getString("name"), item.getString("previewPng"))
                }, strings("imagePool"), specials("specialPhrases", "phrase"), specials("specialImages", "image"))
        }
    }
}

internal fun normalContentOffset(date: LocalDate, anchor: LocalDate, specials: Set<MonthDay>): Long {
    fun occurrencesBefore(day: LocalDate, special: MonthDay): Long {
        val year = day.year.toLong() - 1
        var total = if (special == MonthDay.of(2, 29)) year / 4 - year / 100 + year / 400 else year
        if (special.isValidYear(day.year) && special.atYear(day.year).isBefore(day)) total++
        return total
    }
    return ChronoUnit.DAYS.between(anchor, date) - specials.sumOf {
        occurrencesBefore(date, it) - occurrencesBefore(anchor, it)
    }
}

internal fun shuffledImageIndex(slot: Long, count: Int, seed: Long): Int {
    if (count == 0) return 0
    require(count in 1..512)
    val cycle = Math.floorDiv(slot, count.toLong())
    val position = Math.floorMod(slot, count.toLong()).toInt()
    var state = (seed xor ((cycle * 0x9e3779b9L) and 0xffffffffL)) and 0xffffffffL
    if (state == 0L) state = 0x6d2b79f5L
    val order = IntArray(count) { it }
    for (index in count - 1 downTo 1) {
        state = state xor ((state shl 13) and 0xffffffffL)
        state = state xor (state ushr 17)
        state = state xor ((state shl 5) and 0xffffffffL)
        val other = (state % (index + 1)).toInt()
        val saved = order[index]; order[index] = order[other]; order[other] = saved
    }
    return order[position]
}
