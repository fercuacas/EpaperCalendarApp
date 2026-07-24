package com.fernando.epapercalendar.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.fernando.epapercalendar.calendar.*
import java.time.*
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalCalendarPalette.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .tutorialTarget(TutorialFocusTarget.SEARCH_FIELD)
            .padding(bottom = 14.dp),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.accent)
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Cerrar búsqueda", tint = palette.textMuted)
            }
        },
        placeholder = { Text("Buscar evento") },
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.softLine,
            focusedContainerColor = palette.surface.copy(alpha = 0.86f),
            unfocusedContainerColor = palette.surface.copy(alpha = 0.86f)
        )
    )
}

@Composable
internal fun FilterBar(
    categoryFilter: EventCategory?,
    dateFilter: DateFilter,
    searchVisible: Boolean,
    onCategoryFilterChange: (EventCategory?) -> Unit,
    onDateFilterChange: (DateFilter) -> Unit,
    onClear: () -> Unit
) {
    val palette = LocalCalendarPalette.current
    var categoriesExpanded by remember { mutableStateOf(false) }
    val filtersActive = categoryFilter != null || dateFilter != DateFilter.ALL
    val showCategories = searchVisible || categoriesExpanded || categoryFilter != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = dateFilter == DateFilter.TODAY,
                onClick = { onDateFilterChange(if (dateFilter == DateFilter.TODAY) DateFilter.ALL else DateFilter.TODAY) },
                label = { Text("Hoy") },
                leadingIcon = {
                    Icon(Icons.Rounded.Today, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = dateFilter == DateFilter.THIS_WEEK,
                onClick = { onDateFilterChange(if (dateFilter == DateFilter.THIS_WEEK) DateFilter.ALL else DateFilter.THIS_WEEK) },
                label = { Text("Semana") },
                leadingIcon = {
                    Icon(Icons.Rounded.ViewWeek, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = showCategories,
                onClick = { categoriesExpanded = !categoriesExpanded },
                label = { Text(categoryFilter?.label ?: "Categorías") },
                leadingIcon = {
                    Icon(Icons.Rounded.FilterList, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                },
                shape = RoundedCornerShape(8.dp)
            )
            if (filtersActive) {
                FilterChip(
                    selected = false,
                    onClick = {
                        onClear()
                        categoriesExpanded = false
                    },
                    label = { Text("Limpiar") },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        AnimatedVisibility(visible = showCategories) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventCategory.entries.forEach { category ->
                    FilterChip(
                        selected = categoryFilter == category,
                        onClick = { onCategoryFilterChange(if (categoryFilter == category) null else category) },
                        label = { Text(category.label) },
                        leadingIcon = { CategoryDot(Color(category.accentColor), size = 18.dp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    }
}
