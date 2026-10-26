package com.fernando.epapercalendar.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.fernando.epapercalendar.personal.PersonalContentCatalog
import com.fernando.epapercalendar.personal.PersonalImage
import com.fernando.epapercalendar.epaper.EpaperPayloadBuilder
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PersonalDayUi(
    val date: LocalDate,
    val phrase: String,
    val imageName: String? = null,
    val bitmap: ImageBitmap? = null,
    val enabled: Boolean = true
)

/** One date/catalog selection supplies both phrase and picture. Never reads real assets in tutorial. */
@Composable
internal fun rememberPersonalDayContent(date: LocalDate, enabled: Boolean): PersonalDayUi {
    if (!enabled) {
        return PersonalDayUi(date, "Organiza tu dia, paso a paso", enabled = false)
    }
    val context = LocalContext.current.applicationContext
    val catalog by produceState<Result<PersonalContentCatalog>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(PersonalContentCatalog.ASSET_PATH).bufferedReader().use {
                PersonalContentCatalog.fromJson(it.readText())
            } }
        }
    }
    val selection = remember(catalog, date) { catalog?.getOrNull()?.forDate(date) }
    val decoded by produceState<Pair<PersonalImage, ImageBitmap?>?>(null, selection?.image) {
        val selectedImage = selection?.image
        value = if (selectedImage == null) null else withContext(Dispatchers.IO) {
            selectedImage to runCatching {
                val bytes = Base64.getDecoder().decode(selectedImage.previewPng)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    // During rapid navigation the old decode can still be held by produceState for a frame.
    // Never label/show that old bitmap as the newly selected day's picture.
    val bitmap = decoded?.takeIf { it.first == selection?.image }?.second
    val phrase = when {
        catalog == null -> "Preparando tu frase…"
        catalog?.isFailure == true -> "No se pudo cargar la frase"
        else -> selection?.phrase ?: EpaperPayloadBuilder.DEFAULT_QUOTE
    }
    return PersonalDayUi(date, phrase, selection?.image?.name, bitmap)
}

@Composable
internal fun PersonalContentImage(content: PersonalDayUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("personal_daily_image"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, LocalCalendarPalette.current.textMuted.copy(alpha = 0.14f))
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            content.bitmap?.let {
                Image(bitmap = it, contentDescription = "Foto del e-paper para ${content.date}: ${content.imageName}",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
            } ?: Icon(Icons.Rounded.ImageNotSupported,
                contentDescription = if (content.enabled) "Foto del e-paper no disponible" else "Imagen de ejemplo del tutorial",
                tint = LocalCalendarPalette.current.textMuted)
        }
    }
}

@Composable
internal fun PersonalDailyImage(modifier: Modifier = Modifier, enabled: Boolean = true, dateOverride: LocalDate? = null) {
    PersonalContentImage(rememberPersonalDayContent(dateOverride ?: LocalDate.now(), enabled), modifier)
}
