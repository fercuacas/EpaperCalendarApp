package com.fernando.epapercalendar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fernando.epapercalendar.calendar.AppSection
import com.fernando.epapercalendar.calendar.CalendarEvent
import com.fernando.epapercalendar.calendar.CalendarOccurrence
import com.fernando.epapercalendar.calendar.CalendarReminderScheduler
import com.fernando.epapercalendar.calendar.CalendarRepository
import com.fernando.epapercalendar.calendar.CalendarState
import com.fernando.epapercalendar.calendar.DateFilter
import com.fernando.epapercalendar.calendar.EventCategory
import com.fernando.epapercalendar.calendar.EventDraft
import com.fernando.epapercalendar.calendar.RecurrenceRule
import com.fernando.epapercalendar.calendar.ReminderOption
import com.fernando.epapercalendar.calendar.toDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun EpaperCalendarApp() {
    val context = LocalContext.current.applicationContext
    val calendarState = remember { CalendarState(CalendarRepository(context), CalendarReminderScheduler(context)) }
    CalendarHome(calendarState = calendarState)
}

internal data class CalendarPalette(
    val name: String,
    val description: String,
    val background: List<Color>,
    val surface: Color,
    val surfaceStrong: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentSecondary: Color,
    val softLine: Color,
    val iconMuted: Color,
    val success: Color,
    val sun: Color
)

internal enum class CalendarThemeOption(val palette: CalendarPalette) {
    Warm(
        CalendarPalette(
            name = "Actual",
            description = "Crema, coral y cacao",
            background = listOf(Color(0xFFFFB29B), Color(0xFFFFE3CF), Color(0xFFFFF7EC)),
            surface = Color(0xFFFFF7EC),
            surfaceStrong = Color(0xFFFFFFFF),
            text = Color(0xFF26140E),
            textMuted = Color(0xFF7A381D),
            accent = Color(0xFFFF6042),
            accentSoft = Color(0xFFFFD2B9),
            accentSecondary = Color(0xFFFF8B52),
            softLine = Color(0xFFF6CDBB),
            iconMuted = Color(0xFFC99B87),
            success = Color(0xFF43E88C),
            sun = Color(0xFFFFB43F)
        )
    ),
    Blue(
        CalendarPalette(
            name = "Blanco azul",
            description = "Limpio, frío y claro",
            background = listOf(Color(0xFFEAF7FF), Color(0xFFF6FBFF), Color(0xFFFFFFFF)),
            surface = Color(0xFFF8FCFF),
            surfaceStrong = Color(0xFFFFFFFF),
            text = Color(0xFF102033),
            textMuted = Color(0xFF375A7A),
            accent = Color(0xFF2F8CFF),
            accentSoft = Color(0xFFDCEEFF),
            accentSecondary = Color(0xFF67B8FF),
            softLine = Color(0xFFCFE6F8),
            iconMuted = Color(0xFF8AA9C1),
            success = Color(0xFF2FD989),
            sun = Color(0xFFFFC857)
        )
    ),
    RoseBlack(
        CalendarPalette(
            name = "Rosa negro",
            description = "Blanco, rosa y tinta",
            background = listOf(Color(0xFFFFD8E5), Color(0xFFFFF5F8), Color(0xFFFFFFFF)),
            surface = Color(0xFFFFF7FA),
            surfaceStrong = Color(0xFFFFFFFF),
            text = Color(0xFF171015),
            textMuted = Color(0xFF5B344A),
            accent = Color(0xFFE84D88),
            accentSoft = Color(0xFFFFD8E7),
            accentSecondary = Color(0xFFFF7FB0),
            softLine = Color(0xFFF2C7D7),
            iconMuted = Color(0xFF9E8190),
            success = Color(0xFF35D77D),
            sun = Color(0xFFFFB84E)
        )
    )
}

internal val LocalCalendarPalette = compositionLocalOf { CalendarThemeOption.Warm.palette }

internal enum class RoboferState {
    Resting,
    Active,
    Success
}

internal data class QuickEventTemplate(
    val label: String,
    val title: String,
    val category: EventCategory,
    val durationMinutes: Int = 30,
    val allDay: Boolean = false
)

internal val quickEventTemplates = listOf(
    QuickEventTemplate("Reunión", "Reunión", EventCategory.WORK, 45),
    QuickEventTemplate("Gym", "Gym", EventCategory.FITNESS, 60),
    QuickEventTemplate("Café", "Café", EventCategory.COFFEE, 45),
    QuickEventTemplate("Tarea", "Tarea", EventCategory.TASK, 30),
    QuickEventTemplate("Viaje", "Viaje", EventCategory.PERSONAL, allDay = true)
)

@Composable
private fun CalendarHome(calendarState: CalendarState) {
    val localContext = LocalContext.current
    val context = localContext.applicationContext
    val preferences = remember { context.getSharedPreferences("epaper_calendar", 0) }
    var editorDraft by remember { mutableStateOf<EventDraft?>(null) }
    var importedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var deviceCalendarsVisible by remember { mutableStateOf(false) }
    var createSheetVisible by remember { mutableStateOf(false) }
    val editorVisible = createSheetVisible || editorDraft != null
    var searchVisible by remember { mutableStateOf(false) }
    var epaperSetupVisible by remember { mutableStateOf(false) }
    var roboferState by remember { mutableStateOf(RoboferState.Resting) }
    var notificationDialogVisible by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        notificationDialogVisible = false
    }
    var selectedTheme by remember {
        mutableStateOf(
            runCatching {
                CalendarThemeOption.valueOf(preferences.getString("selected_theme", CalendarThemeOption.Warm.name) ?: CalendarThemeOption.Warm.name)
            }.getOrDefault(CalendarThemeOption.Warm)
        )
    }
    var tutorialTheme by remember { mutableStateOf(selectedTheme) }
    var welcomeVisible by remember { mutableStateOf(!preferences.getBoolean("welcome_seen", false)) }
    var tutorialActive by remember { mutableStateOf(false) }
    var tutorialStepIndex by remember { mutableStateOf(0) }
    var tutorialSessionKey by remember { mutableStateOf(0) }
    val tutorialCalendarState = remember(tutorialSessionKey) {
        CalendarState(
            repository = CalendarRepository(context),
            initialEvents = emptyList(),
            persistChanges = false
        )
    }
    val activeCalendarState = if (tutorialActive) tutorialCalendarState else calendarState
    val deviceCalendars = rememberDeviceCalendars(calendarState, enabled = !tutorialActive)
    LaunchedEffect(importedEvent?.id, deviceCalendars.snapshot, deviceCalendars.permissionGranted) {
        importedEvent?.let { current ->
            importedEvent = if (deviceCalendars.permissionGranted) calendarState.events.firstOrNull { it.id == current.id } else null
        }
    }
    val activeTheme = if (tutorialActive) tutorialTheme else selectedTheme
    val currentTutorialStep = tutorialSteps.getOrElse(tutorialStepIndex) { tutorialSteps.last() }
    val tutorialTargets = remember { mutableStateMapOf<TutorialFocusTarget, androidx.compose.ui.geometry.Rect>() }
    var appMenuVisible by remember { mutableStateOf(false) }
    val palette = activeTheme.palette

    fun closeEditorSurface() {
        createSheetVisible = false
        editorDraft = null
        if (roboferState == RoboferState.Active) {
            roboferState = RoboferState.Resting
        }
    }

    fun startTutorial() {
        preferences.edit().putBoolean("welcome_seen", true).apply()
        welcomeVisible = false
        appMenuVisible = false
        editorDraft = null
        createSheetVisible = false
        searchVisible = false
        roboferState = RoboferState.Resting
        tutorialTheme = selectedTheme
        tutorialStepIndex = 0
        tutorialSessionKey += 1
        tutorialActive = true
    }

    fun finishTutorial() {
        tutorialActive = false
        tutorialStepIndex = 0
        appMenuVisible = false
        searchVisible = false
        closeEditorSurface()
    }

    fun enterApp() {
        preferences.edit().putBoolean("welcome_seen", true).apply()
        welcomeVisible = false
    }

    LaunchedEffect(welcomeVisible, tutorialActive) {
        if (!welcomeVisible && !tutorialActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyAsked = preferences.getBoolean("notification_prompt_seen", false)
            val granted = ContextCompat.checkSelfPermission(
                localContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!alreadyAsked && !granted) {
                notificationDialogVisible = true
            }
        }
    }

    fun openCreateSheet() {
        editorDraft = null
        roboferState = RoboferState.Active
        createSheetVisible = true
    }

    fun openCreateFromTemplate(template: QuickEventTemplate) {
        val date = activeCalendarState.selectedDate
        val startMinutes = if (template.allDay) 0 else 9 * 60
        val endMinutes = if (template.allDay) 23 * 60 + 59 else startMinutes + template.durationMinutes
        editorDraft = EventDraft(
            title = template.title,
            location = "",
            date = date,
            endDate = if (template.allDay) date.plusDays(2) else date,
            startLabel = CalendarEvent.minutesToLabel(startMinutes),
            endLabel = CalendarEvent.minutesToLabel(endMinutes),
            category = template.category,
            allDay = template.allDay
        )
        roboferState = RoboferState.Active
        createSheetVisible = true
    }

    fun advanceTutorial() {
        if (tutorialStepIndex >= tutorialSteps.lastIndex) {
            finishTutorial()
        } else {
            tutorialStepIndex += 1
        }
    }

    fun createTutorialGymEvent() {
        val draft = tutorialGymDraft()
        tutorialCalendarState.saveEvent(draft)
        tutorialCalendarState.selectDate(draft.date)
        createSheetVisible = false
        editorDraft = null
        roboferState = RoboferState.Success
    }

    fun handleTutorialTargetClick() {
        when (currentTutorialStep.focusTarget) {
            TutorialFocusTarget.ROBOFER -> openCreateSheet()
            TutorialFocusTarget.ADD_PANEL -> Unit
            TutorialFocusTarget.CREATE_EVENT_BUTTON -> createTutorialGymEvent()
            TutorialFocusTarget.AGENDA_LIST -> {
                editorDraft = tutorialCalendarState.events
                    .firstOrNull { it.id == TUTORIAL_GYM_EVENT_ID }
                    ?.toDraft()
                createSheetVisible = editorDraft != null
            }
            TutorialFocusTarget.TASKS_TAB -> activeCalendarState.selectSection(AppSection.TASKS)
            TutorialFocusTarget.TASK_ROW -> {
                tutorialCalendarState.events
                    .firstOrNull { it.id == TUTORIAL_TASK_EVENT_ID }
                    ?.let { tutorialCalendarState.toggleDone(it.id) }
            }
            TutorialFocusTarget.SEARCH_BUTTON -> {
                activeCalendarState.selectSection(AppSection.WEEK)
                searchVisible = true
            }
            TutorialFocusTarget.SEARCH_FIELD -> {
                activeCalendarState.selectSection(AppSection.WEEK)
                searchVisible = true
                activeCalendarState.searchQuery = "gym"
            }
            TutorialFocusTarget.WEEK_TAB -> activeCalendarState.selectSection(AppSection.WEEK)
            TutorialFocusTarget.MONTH_TAB -> activeCalendarState.selectSection(AppSection.CALENDAR)
            TutorialFocusTarget.EDITOR_MORE_OPTIONS -> Unit
            TutorialFocusTarget.EDIT_PANEL -> Unit
            TutorialFocusTarget.DATE_STRIP,
            TutorialFocusTarget.FILTERS,
            TutorialFocusTarget.EDITOR_BASICS,
            TutorialFocusTarget.EDITOR_ADVANCED,
            TutorialFocusTarget.EDIT_ACTIONS,
            TutorialFocusTarget.TASKS_VIEW,
            TutorialFocusTarget.TOP_MENU -> Unit
            TutorialFocusTarget.WHOLE_APP,
            TutorialFocusTarget.AGENDA_TAB,
            TutorialFocusTarget.WEEK_VIEW,
            TutorialFocusTarget.MONTH_VIEW -> Unit
        }
        advanceTutorial()
    }

    LaunchedEffect(roboferState) {
        if (roboferState == RoboferState.Success) {
            delay(2200)
            roboferState = RoboferState.Resting
        }
    }

    LaunchedEffect(tutorialActive, tutorialStepIndex, tutorialSessionKey) {
        if (!tutorialActive) return@LaunchedEffect
        tutorialCalendarState.clearFilters()
        tutorialCalendarState.searchQuery = currentTutorialStep.searchQuery
        tutorialCalendarState.selectSection(currentTutorialStep.section)
        applyTutorialDemoAction(tutorialCalendarState, currentTutorialStep.demoAction)
        searchVisible = currentTutorialStep.showSearch
        appMenuVisible = false
        createSheetVisible = currentTutorialStep.openCreatePanel
        roboferState = if (currentTutorialStep.openCreatePanel) RoboferState.Active else RoboferState.Resting
        editorDraft = when (currentTutorialStep.demoAction) {
            TutorialDemoAction.CREATE_READY -> tutorialGymDraft(id = null)
            TutorialDemoAction.EDIT_GYM_READY -> tutorialCalendarState.events
                .firstOrNull { it.id == TUTORIAL_GYM_EVENT_ID }
                ?.toDraft()
            else -> null
        }
    }

    CompositionLocalProvider(
        LocalCalendarPalette provides palette,
        LocalTutorialTargetRegistry provides tutorialTargets
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(palette.background))
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentWindowInsets = WindowInsets(0.dp),
                containerColor = Color.Transparent,
                bottomBar = {
                    BottomCalendarBar(
                        selectedSection = activeCalendarState.section,
                        onSectionSelected = activeCalendarState::selectSection,
                        roboferState = roboferState,
                        onRoboferClick = ::openCreateSheet
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 18.dp)
                ) {
                    CalendarTopBar(
                        state = activeCalendarState,
                        searchVisible = searchVisible,
                        onMenuClick = { appMenuVisible = true },
                        onSearchToggle = { searchVisible = !searchVisible }
                    )

                    if (tutorialActive) {
                        TutorialModeBanner()
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (!tutorialActive && !deviceCalendars.busy && !deviceCalendars.readyForPayload) {
                        Text(
                            text = deviceCalendars.status,
                            color = palette.accent,
                            modifier = Modifier.fillMaxWidth().clickable { deviceCalendarsVisible = true }.padding(vertical = 8.dp)
                        )
                    }

                    CalendarFilters(activeCalendarState, searchVisible) {
                        activeCalendarState.searchQuery = ""
                        searchVisible = false
                    }

                    if (activeCalendarState.section != AppSection.WEEK && activeCalendarState.section != AppSection.CALENDAR) {
                        WeekStrip(
                            selectedDate = activeCalendarState.selectedDate,
                            eventCountFor = activeCalendarState::eventCountFor,
                            onDateSelected = activeCalendarState::selectDate
                        )
                    }

                    when (activeCalendarState.section) {
                        AppSection.AGENDA -> AgendaSection(
                            state = activeCalendarState,
                            personalContentEnabled = !tutorialActive,
                            onEventClick = { if (it.isReadOnly) importedEvent = it else editorDraft = it.toDraft() },
                            onQuickCreate = ::openCreateFromTemplate,
                            onCreate = ::openCreateSheet
                        )

                        AppSection.WEEK -> WeekSection(
                            state = activeCalendarState,
                            onImportedEventClick = { importedEvent = it },
                            onQuickCreate = ::openCreateFromTemplate
                        )

                        AppSection.TASKS -> TasksSection(
                            state = activeCalendarState,
                            onEventClick = { if (it.isReadOnly) importedEvent = it else editorDraft = it.toDraft() },
                            onToggleDone = activeCalendarState::toggleDone,
                            onQuickCreate = ::openCreateFromTemplate
                        )

                        AppSection.CALENDAR -> MonthSection(
                            state = activeCalendarState,
                            onEventClick = { if (it.isReadOnly) importedEvent = it else editorDraft = it.toDraft() },
                            onQuickCreate = ::openCreateFromTemplate
                        )
                    }
                }
            }

            AddEventPanel(
                visible = editorVisible,
                draft = editorDraft,
                selectedDate = activeCalendarState.selectedDate,
                onDismiss = {
                    closeEditorSurface()
                },
                onSave = { draft ->
                    val saved = activeCalendarState.saveEvent(draft)
                    if (saved) {
                        activeCalendarState.selectDate(draft.date)
                        createSheetVisible = false
                        editorDraft = null
                        roboferState = RoboferState.Success
                    }
                    saved
                },
                onSaveRecurring = { draft, mode ->
                    val saved = activeCalendarState.saveRecurringEdit(draft, mode)
                    if (saved) {
                        activeCalendarState.selectDate(draft.date)
                        createSheetVisible = false
                        editorDraft = null
                        roboferState = RoboferState.Success
                    }
                    saved
                },
                onDelete = { id, occurrenceDate, mode ->
                    val deleted = activeCalendarState.deleteEvent(id, occurrenceDate, mode)
                    if (deleted) {
                        editorDraft = null
                        createSheetVisible = false
                    }
                    deleted
                },
                tutorialRevealAdvanced = tutorialActive && currentTutorialStep.revealAdvancedOptions,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 126.dp)
            )
        }

        if (appMenuVisible) {
            AppMenuDialog(
                selectedTheme = activeTheme,
                syncStatus = activeCalendarState.syncStatus,
                onStartTutorial = ::startTutorial,
                onThemeSelected = {
                    if (tutorialActive) {
                        tutorialTheme = it
                    } else {
                        selectedTheme = it
                        preferences.edit().putString("selected_theme", it.name).apply()
                    }
                },
                onConfigureEpaper = {
                    epaperSetupVisible = true
                    appMenuVisible = false
                },
                onConfigureCalendars = {
                    deviceCalendarsVisible = true
                    appMenuVisible = false
                },
                connectionsEnabled = !tutorialActive,
                onDismiss = { appMenuVisible = false }
            )
        }

        if (epaperSetupVisible && !tutorialActive) {
            EpaperSetupDialog(
                state = calendarState,
                deviceCalendars = deviceCalendars,
                onDismiss = { epaperSetupVisible = false }
            )
        }

        if (deviceCalendarsVisible && !tutorialActive) {
            DeviceCalendarsDialog(deviceCalendars, onDismiss = { deviceCalendarsVisible = false })
        }
        if (!tutorialActive) importedEvent?.let { event ->
            ImportedEventDialog(event, onDismiss = { importedEvent = null })
        }

        if (notificationDialogVisible) {
            NotificationPermissionDialog(
                onActivate = {
                    preferences.edit().putBoolean("notification_prompt_seen", true).apply()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        notificationDialogVisible = false
                    }
                },
                onDismiss = {
                    preferences.edit().putBoolean("notification_prompt_seen", true).apply()
                    notificationDialogVisible = false
                }
            )
        }

        if (welcomeVisible) {
            WelcomeDialog(
                onStartTutorial = ::startTutorial,
                onEnterApp = ::enterApp
            )
        }

        if (tutorialActive) {
            TutorialOverlay(
                step = currentTutorialStep,
                stepIndex = tutorialStepIndex,
                totalSteps = tutorialSteps.size,
                targets = tutorialTargets,
                onTargetClick = ::handleTutorialTargetClick,
                onPrevious = {
                    tutorialStepIndex = (tutorialStepIndex - 1).coerceAtLeast(0)
                },
                onNext = {
                    advanceTutorial()
                },
                onExit = ::finishTutorial
            )
        }
    }
}

private const val TUTORIAL_GYM_EVENT_ID = "tutorial-gym-event"
private const val TUTORIAL_TASK_EVENT_ID = "tutorial-task-event"
private const val TUTORIAL_VACATION_EVENT_ID = "tutorial-vacation-event"

private fun tutorialGymDraft(id: String? = TUTORIAL_GYM_EVENT_ID): EventDraft {
    val date = LocalDate.now().plusDays(1)
    return EventDraft(
        id = id,
        title = "Gym",
        location = "Gimnasio",
        date = date,
        endDate = date,
        startLabel = "18:30",
        endLabel = "19:30",
        category = EventCategory.FITNESS,
        people = "Fer",
        reminder = ReminderOption.THIRTY_MINUTES
    )
}

private fun applyTutorialDemoAction(state: CalendarState, action: TutorialDemoAction?) {
    if (action == null) return

    val today = LocalDate.now()

    fun ensureGymEvent() {
        state.saveEvent(tutorialGymDraft())
    }

    fun ensureTaskEvent(done: Boolean = false) {
        state.saveEvent(
            EventDraft(
                id = TUTORIAL_TASK_EVENT_ID,
                title = "Preparar e-paper",
                location = "Mesa de trabajo",
                date = today,
                endDate = today,
                startLabel = "12:00",
                endLabel = "12:30",
                category = EventCategory.TASK,
                notes = "Tarea temporal del tutorial",
                done = done
            )
        )
    }

    fun ensureVacationEvent() {
        state.saveEvent(
            EventDraft(
                id = TUTORIAL_VACATION_EVENT_ID,
                title = "Vacaciones demo",
                location = "Costa",
                date = today.plusDays(3),
                endDate = today.plusDays(9),
                startLabel = "00:00",
                endLabel = "23:59",
                category = EventCategory.PERSONAL,
                allDay = true,
                reminder = ReminderOption.DAY_BEFORE
            )
        )
    }

    when (action) {
        TutorialDemoAction.CREATE_READY -> {
            state.selectDate(today.plusDays(1))
        }

        TutorialDemoAction.EVENT_CREATED -> {
            ensureGymEvent()
            state.selectDate(today.plusDays(1))
        }

        TutorialDemoAction.EDIT_GYM_READY -> {
            ensureGymEvent()
            state.selectDate(today.plusDays(1))
        }

        TutorialDemoAction.TASK_CREATED -> {
            ensureGymEvent()
            ensureTaskEvent()
            state.selectDate(today)
        }

        TutorialDemoAction.TASK_COMPLETED -> {
            ensureGymEvent()
            ensureTaskEvent(done = true)
            state.selectDate(today)
        }

        TutorialDemoAction.SEARCH_READY -> {
            ensureGymEvent()
            ensureTaskEvent()
            state.selectDate(today.plusDays(1))
        }

        TutorialDemoAction.WEEK_READY -> {
            ensureGymEvent()
            ensureVacationEvent()
            state.selectDate(today.plusDays(3))
        }

        TutorialDemoAction.MONTH_READY -> {
            ensureVacationEvent()
            state.selectDate(today.plusDays(3))
        }

        TutorialDemoAction.MENU_READY -> {
            ensureGymEvent()
            ensureTaskEvent()
            ensureVacationEvent()
            state.selectDate(today)
        }
    }
}

@Composable
private fun CalendarTopBar(
    state: CalendarState,
    searchVisible: Boolean,
    onMenuClick: () -> Unit,
    onSearchToggle: () -> Unit
) {
    var monthMenuExpanded by remember { mutableStateOf(false) }
    val palette = LocalCalendarPalette.current
    val monthName = state.visibleMonth.month
        .getDisplayName(TextStyle.FULL, calendarLocale)
        .replaceFirstChar { it.titlecase(calendarLocale) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(
            icon = Icons.Rounded.Menu,
            contentDescription = "Menú",
            onClick = onMenuClick,
            modifier = Modifier.tutorialTarget(TutorialFocusTarget.TOP_MENU)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { monthMenuExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monthName,
                    color = palette.textMuted,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Cambiar mes",
                    tint = palette.textMuted,
                    modifier = Modifier.size(26.dp)
                )
            }
            DropdownMenu(
                expanded = monthMenuExpanded,
                onDismissRequest = { monthMenuExpanded = false }
            ) {
                for (offset in -3..6) {
                    val month = YearMonth.now().plusMonths(offset.toLong())
                    DropdownMenuItem(
                        text = {
                            Text(
                                month.month.getDisplayName(TextStyle.FULL, calendarLocale)
                                    .replaceFirstChar { it.titlecase(calendarLocale) } +
                                    " ${month.year}"
                            )
                        },
                        onClick = {
                            state.selectDate(month.atDay(1))
                            monthMenuExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (state.filtersEnabled) {
            RoundIconButton(
                icon = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = "Buscar",
                onClick = onSearchToggle,
                modifier = Modifier.tutorialTarget(TutorialFocusTarget.SEARCH_BUTTON)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Surface(
            shape = CircleShape,
            color = palette.accentSoft.copy(alpha = 0.72f),
            modifier = Modifier.size(52.dp),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "F",
                    color = palette.textMuted,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarHomePreview() {
    val context = LocalContext.current
    val state = remember { CalendarState(CalendarRepository(context)) }
    CalendarHome(calendarState = state)
}
