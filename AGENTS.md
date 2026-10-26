# AGENTS.md

## Propósito y alcance

Estas instrucciones se aplican a todo el repositorio. Léelas antes de explorar o modificar archivos.

E-paper Calendar es una aplicación Android local de calendario personal, escrita en Kotlin y Jetpack Compose. El repositorio contiene además `foto2ink`, una utilidad gráfica Python independiente para convertir imágenes a un formato e-paper tricolor.

Objetivo de este archivo: evitar exploraciones completas innecesarias, señalar las fuentes de verdad y conservar las decisiones actuales del proyecto.

## Resumen rápido

- Repositorio remoto: `https://github.com/fercuacas/EpaperCalendarApp.git`.
- Módulo Android único: `:app`.
- Namespace/application ID: `com.fernando.epapercalendar`.
- UI: Jetpack Compose + Material 3; no hay layouts XML.
- Idioma principal de interfaz y documentación: español.
- Persistencia: `SharedPreferences` local, archivo lógico `epaper_calendar`, eventos serializados como JSON.
- Red: cliente HTTP local para el ESP32 y descubrimiento Android NSD; no hay backend, cuentas ni nube.
- Calendario del sistema: lectura opcional con `READ_CALENDAR` mediante CalendarContract; sin `WRITE_CALENDAR`, OAuth, escritura ni Google Calendar API.
- E-paper: contrato v2, configuración Wi-Fi y sincronización real con `Ink_calendar32` por LAN/AP.
- `foto2ink`: herramienta de escritorio separada; no está invocada por la app.
- `usb_calendar`: herramienta Tkinter separada de control/pruebas por USB y actualización explícita de firmware. Entrada `usb_calendar/README.md`; no abre puertos ni carga firmware por defecto al iniciar. Requiere confirmación de proyecto/perfil/puerto al pulsar Actualizar. No usar pruebas automatizadas para flashear hardware real.
- Privacidad Git: fotos, `.epd`, previews, frases/reglas locales y `app/src/main/assets/personal_content/` se ignoran, igual que APKs/binarios/diagnósticos. Nunca forzar su alta ni copiar sus datos a fuentes, fixtures o README. `python -B tools/check_public_tree.py` comprueba el índice actual. No reescribir historial: el propietario eligió excluir solo desde esta versión. Mantener intactos los archivos locales. Un clon nuevo requiere preparar/exportar el catálogo según README antes de probar contenido personal.
- Android mínimo: API 26.
- `compileSdk` y `targetSdk`: 36.
- JVM para Gradle: JDK 21.
- Compatibilidad fuente/bytecode Android: Java 11.
- Rama principal observada: `main`.

## Estrategia de lectura para ahorrar contexto

No leas todos los archivos de UI por defecto: varios superan cientos de líneas.

1. Para configuración o compilación, empieza por `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` y `gradle/gradle-daemon-jvm.properties`.
2. Para modelos o recurrencia, lee `calendar/CalendarEvent.kt`.
3. Para reglas y mutaciones, lee `calendar/CalendarState.kt`.
4. Para almacenamiento, lee `calendar/CalendarRepository.kt`.
5. Para notificaciones, lee los tres archivos `CalendarReminder*.kt` y el manifiesto.
6. Para navegación, estado visual o integración general, lee primero las secciones relevantes de `ui/CalendarApp.kt`.
7. Para el formulario o parser natural, busca la función concreta en `ui/AddEventPanel.kt`; no cargues sus ~1.000 líneas sin necesidad.
8. Para una vista, abre solo `AgendaView.kt`, `WeekView.kt` o `MonthView.kt` según la tarea.
9. Para el tutorial, abre `TutorialOverlay.kt`, `TutorialTargets.kt` y después los usos de `tutorialTarget`.
10. Para la herramienta Python, usa `foto2ink/README.md` como entrada y abre `foto2ink.py` solo si se cambia su comportamiento.

Usa `rg` para localizar símbolos y textos antes de abrir archivos grandes. Excluye normalmente `.git/`, `.gradle/`, `.kotlin/`, `.idea/`, `build/`, `app/build/`, `__pycache__/` y binarios.

## Flujo arquitectónico

```text
MainActivity
  -> EpaperCalendarApp / CalendarHome (Compose)
     -> CalendarState (estado observable + reglas)
        -> CalendarRepository (SharedPreferences + JSON)
        -> CalendarReminderScheduler (AlarmManager)
           -> CalendarReminderReceiver (notificación y próxima recurrencia)
           -> CalendarReminderBootReceiver (restauración tras reinicio)
```

No hay ViewModel, Room, capa de casos de uso, inyección de dependencias ni navegación de múltiples actividades. `CalendarState` es una clase Compose mantenida con `remember` durante la actividad.

## Mapa de archivos

### Configuración raíz

- `settings.gradle.kts`: repositorios y módulo `:app`.
- `build.gradle.kts`: Android Gradle Plugin 8.13.2 en `buildscript`.
- `gradle.properties`: memoria, configuration cache, AndroidX y estilo Kotlin.
- `gradle/libs.versions.toml`: versiones y aliases de dependencias/plugins.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.4.1.
- `gradle/gradle-daemon-jvm.properties`: JVM del daemon fijada a 21 y URLs de toolchains.
- `app/build.gradle.kts`: SDK, application ID, Compose, Java 11 y dependencias.
- `app/src/main/AndroidManifest.xml`: permisos, actividad y receivers.
- `README.md`: guía humana de instalación, uso, arquitectura y limitaciones.
- `LICENSE`: Apache License 2.0.

### Entrada y dominio Android

- `MainActivity.kt`: actividad única, edge-to-edge y montaje de `EpaperCalendarApp`.
- `calendar/CalendarEvent.kt`:
  - `CalendarEvent`: entidad persistida.
  - `CalendarOccurrence`: aparición calculada de un evento.
  - `EventDraft`: datos editables del formulario.
  - `EventCategory`, `RecurrenceRule`, `ReminderOption`, `RecurrenceEditMode`.
  - conversión de minutos, búsqueda normalizada y cálculo de apariciones.
- `calendar/CalendarState.kt`:
  - lista observable de eventos;
  - fecha/mes/sección seleccionados;
  - búsqueda y filtros;
  - consultas por día, rango, mes, próximas apariciones y tareas;
  - creación/edición/borrado/completado;
  - división de series recurrentes;
  - generación del payload e-paper.
  - define `AppSection` y `DateFilter`.
- `calendar/CalendarRepository.kt`: carga/guarda eventos y preferencias.
- `calendar/device/DeviceCalendarModels.kt`: identidades de calendario/cuenta, origen opcional, rango de 13 meses y conversión pura de instancias a eventos de solo lectura.
- `calendar/device/AndroidDeviceCalendarReader.kt`: lecturas CalendarContract en IO, recurrencias/excepciones expandidas por Instances; jamás insertar/actualizar/borrar en el proveedor.
- `calendar/device/DeviceCalendarRepository.kt`: SharedPreferences separado `device_calendar_import`, JSON v1 con selección y snapshot, sin datos demo.
- `calendar/device/DeviceCalendarController.kt`: exclusión mutua, revisiones contra resultados obsoletos, permisos, caché y preflight e-paper.
- `calendar/CalendarReminderScheduler.kt`: agenda/cancela la próxima alarma.
- `calendar/CalendarReminderReceiver.kt`: canal `calendar_reminders`, notificación y reprogramación.
- `calendar/CalendarReminderBootReceiver.kt`: reprograma todos los eventos al recibir `BOOT_COMPLETED`.

### UI Compose

- `ui/CalendarApp.kt`: raíz funcional de UI. Contiene:
  - creación de repositorio, scheduler y estado;
  - paletas y `CalendarThemeOption`;
  - plantillas rápidas;
  - bienvenida y solicitud de notificaciones;
  - estado temporal del tutorial;
  - coordinación del editor, menú y payload;
  - top bar y selección de sección.
- `ui/AddEventPanel.kt`: editor de eventos, validación visual, acciones, selectores, diálogo de edición recurrente y parser de lenguaje natural.
- `ui/AgendaView.kt`: frase del día seleccionado con contador de tareas pendientes debajo (sin saludo, comillas ni carrusel), eventos de todo el día, integración de cuadrícula y lista de tareas.
- `ui/PersonalDailyImage.kt`: `rememberPersonalDayContent(selectedDate, enabled)` obtiene frase/foto juntas para el día seleccionado, NO hoy fijo. `PersonalDayUi`/`PersonalContentImage` separan datos de dibujo; IO/Bitmap fuera del hilo principal. Al cambiar fecha, el resultado de decodificación lleva su PersonalImage y solo se muestra si coincide con la selección actual, sin imágenes antiguas mal etiquetadas. Tutorial retorna frase/placeholder demo antes de leer assets. Sin red ni rutas PC en runtime. El wrapper PersonalDailyImage admite fecha explícita; AgendaSection pasa state.selectedDate a SummaryPanel.
- `SummaryPanelContent`: tarjeta de 24 dp de radio y padding18, fila medida (columna weight1 con frase completa como título y contador debajo + foto cuadrada104 dp con marco20/imagen12 dp de radio), sin saludo ni bloque inferior de cita, posiciones absolutas o altura fija/ellipsis. Una frase larga/letra grande debe aumentar altura, nunca tapar foto/contador. `PersonalImageInstrumentedTest` prueba navegación real, vuelta/cambios rápidos, geometría a320 dp/1,6× y las tres paletas; capturas en caché del emulador. DEFAULT_QUOTE en EpaperPayloadBuilder comparte el fallback con UI sin cambiar protocolo.
- `personal/PersonalContent.kt`: selección pura espejo de Ink_calendar32/PersonalContent.cpp: ancla/semilla compartidas, frases ordenadas, fotos Fisher–Yates/xorshift32 uint32 con floorDiv/floorMod, excepciones MM-DD pausan solo su ciclo. Feb29 solo en bisiestos. No usar Random de Kotlin ni NVS como ancla independiente.
- `app/src/main/assets/personal_content/catalog.json`: contenido fuente portátil exportado (PNG base64 + reglas + fingerprint + golden checks), no caché ni preferencias. `tools/sync_personal_content.py --ink-root <ruta explícita>` lo actualiza desde el generador del firmware; no editarlo a mano, no almacenar rutas locales. Después recompilar APK y firmware desde los mismos recursos. No transfiere imágenes por red. EpaperSetupDialog compara assetsVersion/fecha en Probar y al enviar; avisa sin impedir la sincronización de eventos. Pruebas PersonalContentTest/PersonalImageInstrumentedTest.
- `ui/DayTimeline.kt`: cuadrícula diaria de 24 h con scroll propio acotado a 440 dp; abre cada fecha a las 08:00 sin saltar al refrescar eventos. Conserva clic y origen importado. No anidar un scroll vertical de altura infinita en CalendarApp.
- `calendar/DayTimelineLayout.kt`: cálculo puro de grupos de solapamiento, columnas reutilizables y expansión a columnas libres; extremos exclusivos, mínimo visual de 15 minutos sin cambiar datos. Todo el día/varios días siguen separados. Pruebas en DayTimelineLayoutTest y DayAgendaInstrumentedTest.
- `ui/WeekView.kt`: resumen semanal y eventos de todo el día/varios días. `ui/WeekTimeline.kt` dibuja la cuadrícula continua de 24 h, con scroll acotado a 440 dp y apertura a las 08:00 por semana; no comprimir huecos ni añadir separadores de mañana/tarde/noche.
- `calendar/WeekTimelineLayout.kt`: agrupa eventos por minuto exacto de inicio para mostrar contadores; conserva finales individuales en franjas y reutiliza el reparto de columnas diario con mínimo de 1 minuto. Distintos inicios se distribuyen en paralelo. Pruebas WeekTimelineLayoutTest y WeekTimelineInstrumentedTest.
- `ui/MonthView.kt`: cabecera, cuadrícula, métricas y rangos de varios días.
- `ui/Filters.kt`: campo de búsqueda y chips de filtros. `CalendarFilters` y el botón de búsqueda solo se muestran si `CalendarState.filtersEnabled` (WEEK/CALENDAR). Las dos variantes de `matchesFilters` ignoran búsqueda/categoría/fecha en AGENDA/TASKS sin borrar preferencias. El payload sigue sin filtros en cualquier sección. El tutorial de búsqueda usa WEEK.
- `ui/PeriodNavigation.kt`: cabecera compartida con flechas accesibles de 48 dp para Semana/Mes. `CalendarState.navigateWeek(±1)` conserva día semanal y `navigateMonth(±1)` limita al último día cuando falta el número de día; ambas usan selectDate para actualizar visibleMonth. No desincronizar selección, importaciones y cabeceras. Pruebas CalendarNavigationTest/CalendarNavigationInstrumentedTest cubren filtros, flechas, cambios de año y febrero bisiesto.
- `ui/RoboferButton.kt`: botón animado y navegación Agenda/Semana/Tareas/Mes.
- `ui/ThemePicker.kt`: menú, tutorial, temas, permiso de notificaciones y diálogo del payload.
- `ui/EpaperSetupDialog.kt`: host/IP, NSD, acceso a ajustes Wi-Fi, provisionado, prueba, preview y sincronización.
- `ui/DeviceCalendarsUi.kt`: configuración de calendarios, ficha importada, etiquetas y binding lifecycle/ContentObserver (500 ms, solo visible y fuera del tutorial).
- `epaper/EpaperPayload.kt`: contrato v2, expansión de recurrencias y límites 96 KiB/768.
- `epaper/EpaperHttpClient.kt`: transporte local `HttpURLConnection`.
- `epaper/EpaperNsdDiscovery.kt`: `_inkcalendar._tcp` mediante Android NSD.
- `ui/TutorialOverlay.kt`: pasos, datos y overlay del tutorial.
- `ui/TutorialCalloutLayout.kt`: mide y coloca la tarjeta del tutorial en coordenadas de la raíz, descontando `WindowInsets.safeDrawing` (barras/cámara) y márgenes; elige el mayor hueco encima/debajo del foco. No aplicar padding al overlay completo: desalinearía los rectángulos de `boundsInRoot`. Texto desplazable y botones fijos; previews acotadas y medidas para no taparlas. Pruebas TutorialCalloutLayoutTest/TutorialLayoutInstrumentedTest.
- `ui/TutorialTargets.kt`: registro de rectángulos para destacar controles.
- `ui/SharedUi.kt`: componentes compartidos, cabeceras, estados vacíos y plantillas rápidas.
- `ui/theme/`: tema Material base, tipografía y colores generados/iniciales.

### Recursos y pruebas

- `res/values/strings.xml`: actualmente solo `app_name`; muchos textos están inline en Compose.
- `res/values/themes.xml`: tema Android de arranque.
- `res/mipmap-*` y `res/drawable/`: icono de la aplicación.
- `res/xml/backup_rules.xml` y `data_extraction_rules.xml`: excluyen `device_calendar_import.xml` de backup y transferencia; los demás datos conservan la política existente.
- `src/test/.../ExampleUnitTest.kt`: prueba local de plantilla, sin cobertura real.
- `src/androidTest/.../ExampleInstrumentedTest.kt`: prueba instrumentada de package name.
- `src/main/keepRules/rules.keep`: reglas de conservación, actualmente sin reglas relevantes observadas.

### Herramienta Python

- `foto2ink/foto2ink.py`: aplicación Tkinter + Pillow, cuantización y escritor `.epd`.
- `foto2ink/batch_export.py`: cola de rutas sin cargar todas las imágenes y guardado exclusivo de pares `.epd`/preview con sufijos anticolisión y limpieza de archivos nuevos ante errores.
- `foto2ink/test_batch_export.py`: pruebas headless, sin Tk(), de cola/callbacks/caché/recortes/exportación/errores y compatibilidad INK3; ejecutar `python -B -m unittest foto2ink.test_batch_export -v` desde la raíz.
- `foto2ink/requirements.txt`: `Pillow>=10.4.0`.
- `foto2ink/README.md`: uso y formato de salida.

## Modelo de datos e invariantes

`CalendarEvent` persiste:

- `id`: UUID/string estable.
- `title`: obligatorio.
- `location`, `people`, `notes`: texto opcional.
- `date`, `endDate`: intervalo inclusivo; `endDate >= date`.
- `startMinutes`, `endMinutes`: minutos desde medianoche.
- `category`: `WORK`, `FOCUS`, `COFFEE`, `FITNESS`, `PERSONAL` o `TASK`.
- `allDay`: convierte el horario a 00:00–23:59.
- `recurrenceRule`: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY` o `CUSTOM`.
- `customRepeatDays`: mínimo 1.
- `customWeekDays`: conjunto opcional de `DayOfWeek`; JSON como enteros ISO 1=lunes a 7=domingo. Con `CUSTOM` y conjunto no vacío se repite semanalmente en esos días; vacío conserva el modo heredado cada `customRepeatDays`. Los demás modos lo normalizan a vacío al guardar. Propagar por EventDraft/toDraft y todas las ramas de edición.
- `recurrenceEndDate`: opcional.
- `excludedDates`: apariciones omitidas al editar una sola recurrencia.
- `reminder`: `NONE`, `FIVE_MINUTES`, `THIRTY_MINUTES` o `DAY_BEFORE`.
- `done`: usado principalmente para tareas.
- `updatedAt`: epoch milliseconds.

Validaciones de `CalendarState.saveEvent`:

- título no vacío;
- etiquetas horarias parseables como `HH:mm`;
- fecha final no anterior a la inicial;
- si no es de todo el día y solo dura un día, fin estrictamente posterior al inicio.

Los eventos de varios días se consideran `allDayLike` en varias vistas y en los recordatorios, aunque `allDay` sea falso.

`CalendarEvent.occurrencesOverlapping` calcula recurrencias bajo demanda. Tiene un guard de 520 iteraciones. No cambies ese límite ni la semántica inclusiva sin revisar vistas, filtros, payload y recordatorios.

`upcomingEvents` busca desde la fecha seleccionada hasta seis meses, ordena apariciones y aplica `distinctBy(id)`: actualmente devuelve como máximo una aparición por serie. Esto es comportamiento existente; si se cambia, revisa el payload y la agenda.

### Edición recurrente

- `SINGLE_OCCURRENCE`: añade la fecha a `excludedDates` de la serie y crea un evento independiente no recurrente.
- `THIS_AND_FUTURE`: termina la serie original el día anterior y crea una nueva serie.
- `ENTIRE_SERIES`: reemplaza el evento original mediante `saveEvent`.

Después de dividir una serie se persisten los datos y se reprograman los recordatorios.

El borrado usa `RecurrenceDeleteMode`: SINGLE_OCCURRENCE añade la fecha de inicio original de la aparición a `excludedDates` y reprograma su aviso; ENTIRE_SERIES elimina el evento y cancela el aviso. Validar el inicio real, no un día intermedio de un intervalo ni los campos sin guardar del editor. Los eventos independientes creados al separar una aparición no se borran con otra serie. Los importados siguen protegidos.

`customWeekdayOccurrences` comparte cálculo entre vistas, recordatorios y payload: salta al rango útil, respeta duración inclusiva y exclusiones. El guard general heredado de 520 no se cambia; el payload pide 769 apariciones para detectar exceso sin truncarlo. No reinterpretar JSON antiguo que no tenga `customWeekDays`.

`ui/RecurrenceControls.kt` contiene el selector accesible y el diálogo de borrado. `RecurrenceBehaviorTest.kt` y `RecurrenceInstrumentedTest.kt` cubren modelo/estado/payload/recordatorios, persistencia compatible y UI. `nextReminderTriggerMillis` es el cálculo puro usado por el scheduler, con reloj y zona inyectables para pruebas.

## Persistencia

### Calendarios importados (separados de los eventos locales)

- `CalendarEvent.deviceSource` es opcional; null significa local. `isReadOnly` deriva de ese origen. `copy` y `CalendarOccurrence.asCalendarEvent` lo conservan; no se añade a EventDraft ni al protocolo v2.
- `CalendarState` combina locales/importados solo para consultas, vistas y payload. `persist`, recordatorios y mutaciones operan únicamente sobre locales; los IDs `device:` también se rechazan en mutaciones. `CalendarStore` permite probar dominio sin Context.
- `CalendarRepository` conserva lectura/escritura del JSON antiguo; filtra eventos de origen externo al guardar como defensa adicional.
- Almacenamiento importado: archivo lógico `device_calendar_import`, clave `data`, raíz `version=1`, selección por ID+cuenta+tipo y snapshot con rango, zona, instante y eventos/origen. No restaurar este archivo en otro móvil.
- Instances usa finales exclusivos; el modelo tiene finales inclusivos. Todo el día se interpreta en UTC, horarios en la zona del móvil. Una medianoche exclusiva no añade un día; recurrencias importadas se representan ya expandidas con `NONE` y sin recordatorios.
- Refresco serializado en el controlador. Cambiar selección o rango invalida lecturas en curso antes de esperar al mutex; no publicar resultados obsoletos. Un snapshot correcto sustituye al anterior, no se acumula.
- Quitar un calendario retira sus apariciones inmediatamente. Cuenta/ID deben coincidir; no asociar IDs reutilizados a otra cuenta. Errores conservan la copia con aviso y bloquean preflight; revocar permiso elimina datos importados, conservando selección técnica.
- `prepareEpaperPayload` actualiza y espera si hay calendarios seleccionados; usarlo en preview/envío. `CalendarState.buildEpaperPayload` también impide exportar si el controlador ha marcado importaciones no preparadas. No truncar para cumplir límites.
- No usar WorkManager ni pedir permisos en el arranque. Solicitar solo READ_CALENDAR al pulsar Permitir acceso en el menú; refrescar al abrir/reanudar, cambiar mes y eventos del proveedor mientras visible. No forzar la sincronización de Google ni enviar automáticamente al ESP32.
- Pruebas: `DeviceCalendarTest.kt` (conversión, controlador, caché, dominio/payload), `DeviceCalendarsInstrumentedTest.kt` (UI y preferencias aisladas) y `DeviceCalendarProviderInstrumentedTest.kt` (lectura del proveedor real con ID inexistente, sin insertar eventos). `org.json:json:20240303` solo existe en testImplementation para ejecutar el codec en JVM. Espresso 3.7.0 y ext.junit 1.3.0 evitan APIs de reflexión retiradas en Android reciente; están en el catálogo de versiones.

### Eventos y preferencias locales

Fuente: `CalendarRepository` y preferencias directas de `CalendarApp`.

SharedPreferences: `epaper_calendar`.

Claves:

- `events`: array JSON completo.
- `selected_section`: `AppSection`.
- `category_filter`: nombre de `EventCategory`, o ausente.
- `date_filter`: `DateFilter`.
- `selected_theme`: `CalendarThemeOption`.
- `welcome_seen`: boolean.

No se persisten `selectedDate`, `visibleMonth`, `searchQuery` ni el estado del editor/tutorial.

Si `events` no existe o el JSON no puede leerse, `loadEvents()` devuelve eventos demo. En la inicialización real, `CalendarState` vuelve a persistir el resultado, por lo que la primera ejecución materializa esos datos.

Compatibilidad existente:

- Una sección antigua con valor `DAY` migra en lectura a `AGENDA`.
- Enums desconocidos de categoría/recurrencia/recordatorio vuelven a valores seguros.
- No hay versión formal del esquema ni migraciones de base de datos.

Al añadir o renombrar campos:

1. Mantén valores por defecto compatibles en `toCalendarEvent`.
2. Actualiza `toJson`.
3. Revisa `EventDraft`, `toDraft`, `calendarEventFromDraft` y todas las ramas de edición recurrente.
4. Añade pruebas de lectura de datos antiguos.

## UI, navegación y estado

Secciones (`AppSection`):

- `AGENDA`
- `WEEK`
- `TASKS`
- `CALENDAR` (etiquetada como Mes)

Temas (`CalendarThemeOption`):

- `Warm`: “Actual”.
- `Blue`: “Blanco azul”.
- `RoseBlack`: “Rosa negro”.

Plantillas rápidas:

- Reunión: Trabajo, 45 min.
- Gym: Fitness, 60 min.
- Café: Café, 45 min.
- Tarea: Tarea, 30 min.
- Viaje: Personal, todo el día.

El tutorial crea otro `CalendarState` con `persistChanges = false` e `initialEvents` propios. Ninguna acción del tutorial debe escapar al repositorio o scheduler real. Al cambiar código del tutorial, verifica esta separación.

La bienvenida se muestra mientras `welcome_seen == false`. La solicitud personalizada de notificaciones aparece después de salir de la bienvenida y fuera del tutorial en Android 13+.

El parser de `AddEventPanel`:

- normaliza mayúsculas y diacríticos;
- reconoce hoy, mañana, pasado mañana y días de la semana;
- reconoce horas con `:`, `h` o `.`;
- infiere algunas categorías por palabras clave;
- produce una sugerencia que el usuario debe aplicar.

No es IA ni procesamiento remoto. Conserva esa expectativa en UI y documentación.

## Recordatorios y permisos

Permisos del manifiesto:

- `android.permission.POST_NOTIFICATIONS`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`
- `android.permission.READ_CALENDAR` (solo lectura opcional de calendarios del móvil)

No hay permiso de alarmas exactas; se usa `AlarmManager.set`, no `setExact`.

Flujo:

1. Guardar un evento llama `CalendarReminderScheduler.schedule`.
2. Se cancela primero el `PendingIntent` anterior, identificado con `event.id.hashCode()`.
3. Se busca la próxima aparición entre ayer y dos años desde hoy.
4. Eventos `allDayLike` comienzan a medianoche para calcular el aviso.
5. El receiver muestra una notificación y agenda la siguiente aparición de la serie.
6. El boot receiver recarga y reprograma todos los eventos.

El canal se llama `calendar_reminders` y se muestra al usuario como “Recordatorios”.

Al modificar recordatorios, prueba al menos:

- evento futuro normal;
- evento de todo el día;
- evento recurrente;
- edición y borrado;
- permiso denegado en API 33+;
- reinicio del dispositivo/emulador;
- cambio de zona horaria/horario de verano si afecta la tarea.

## Payload e-paper

`CalendarState.buildEpaperPayload()` delega en `EpaperPayloadBuilder`. El contrato v2 sincroniza el primer día de seis meses antes hasta el último día de seis meses después del mes seleccionado:

- raíz obligatoria: `version=2`, `generatedAt`, `timezone`, `today`, `selectedDate`, `rangeStart`, `rangeEnd`, `calendarName`, `quote`, `events`;
- evento obligatorio: `occurrenceId`, `date`, `endDate`, `time`, `end`, `title`, `category`, `allDay`, `done`;
- recurrencias expandidas en Android, sin filtros visuales;
- tareas completadas omitidas;
- máximo 768 apariciones y 96 KiB UTF-8;
- texto visible transliterado a ASCII para las fuentes del firmware.

Transporte y descubrimiento:

- `GET /status`, `POST /config/wifi`, `POST /calendar`;
- `HttpURLConnection` siempre en `Dispatchers.IO`;
- NSD `_inkcalendar._tcp.` más host manual y acceso directo `192.168.4.1`;
- solo el host se guarda como `epaper_host`; la contraseña Wi-Fi doméstica no se persiste en Android;
- HTTP cleartext está habilitado para la LAN/AP y no debe exponerse a Internet.

## Controlador de escritorio USB

`usb_calendar/controller.py` contiene transporte v1 correlacionado por ID, lease de 5 minutos, worker serie único, scheduler automático desde el final de cada refresco y DEMO sin hardware. `usb_calendar/usb_calendar.py` contiene Tk, pestañas Navegar/Automático/Actualizar y monitor; no tocar UI Android para estas tareas. Solo hilo Tk accede a widgets, los workers publican por queue. PARAR no interrumpe un refresco en marcha. Firmware antiguo se rechaza en control pero se puede actualizar desde la pestaña Actualizar.

Conexión: reintentar solo status de negociación y status/hold idempotentes, con ID correlacionado y plazo global; nunca date/step/exit/refresh/time. ConnectionCancelled permite cancelar la negociación sin esperar 45 s. connection_stage explica progreso/error. Tras await_display inicial emitir busy=false antes de connected; de lo contrario los botones quedan grises. Se conserva DTR/RTS sin pulsos de reset y la renovación hold cada 30 s. Pruebas de primera respuesta perdida, hold perdido, timeout/cancelación y refresco inicial en test_controller. Última aceptación GUI interrumpida por el usuario: no confundir pruebas headless con verificación física.

`Sincronizar fecha con el PC…` es una mutación explícita del RTC/checkpoint, confirmada, distinta de Hoy en pruebas. Acción interna `sync_time`, nunca texto libre en command_bytes. SerialSession.synchronize_time exige hasCalendar, toma time.time al ejecutarse, envía `time <UTC>\nusb <id> exit\n` en una escritura (cierra preview antes de refrescar), valida epoch/source/trust en respuesta correlacionada y no reintenta resultados inciertos. Worker espera pantalla, renueva hold y emite time_synced; deshabilitar botón offline/busy/auto/update/closing. Conserva zona configurada del ESP32, eventos y radio off. No sincronizar al conectar ni tras cargar firmware sin acción del usuario. DemoTransport simula el mismo flujo sin puerto. Pruebas con reloj inyectado, rechazo/timeout, guardas UI y espera de refresco en test_controller/test_ui_actions.

`firmware_update.py`: editor de frases (una por línea) y JSON de reglas, validación con generador canónico del proyecto elegido, rechazo de cambios de otro editor, reemplazo atómico por archivo. No sobrescribir fuentes del usuario al probar: fixtures temporales. `FirmwareUpdate` detiene/cierra COM, aparca en ROM (`chip_id`, sin escritura) para que no duerma, compila con PlatformIO, valida fingerprint en header/binario y tamaño, compara tabla compilada con lectura de `0x8000` y escribe exclusivamente app0 `0x10000` con esptool. Sin erase general, bootloader, particiones ni uploadfs. XIAO exige `INK_WIFI_DISABLED=1`; solo perfiles conocidos. Un fallo tras aparcar puede dejar BOOT; no reiniciar/cargar automáticamente como diagnóstico. Regenerar catálogo Android es opcional, no genera APK. No interrumpir carga al cerrar ventana. Comandos subprocess como listas con shell=False; nunca formar comandos shell desde textos del editor.

Pruebas headless: `python -B -m unittest usb_calendar.test_controller usb_calendar.test_firmware_update usb_calendar.test_ui_actions -v`. Fakes para COM/procesos/ventanas; no Tk(), Wi-Fi ni hardware. Para revisar manualmente GUI, usar DEMO y no pulsar carga real salvo petición explícita. Dependencias de control en requirements.txt; compilación opcional en requirements-build.txt. No guardar rutas de máquina en fuentes, no añadir outputs ni binarios. Cualquier prueba real debe distinguir compilación, simulación de carga, carga verificada y comportamiento físico.

## `foto2ink`

Aplicación Tkinter que:

- abre PNG/JPG/JPEG/BMP/WebP;
- permite mover y dimensionar un recorte cuadrado;
- ofrece borrado de fondo desde bordes;
- cuantiza a blanco, negro y rojo;
- tiene modos `Detallado` y `Sticker limpio`;
- permite salida de 64 a 240 px, por defecto 160;
- escribe `*_preview.png` y `.epd`.
- Modo por lotes: selección múltiple, carpeta/tamaño inicial y revisión individual; `Listo → siguiente` o Ctrl+Enter guarda y avanza, sin popup de éxito. Conserva ajustes y porcentaje de recorte, pero centra un recorte nuevo por foto. `Saltar foto` no exporta. Cerrar/cambiar sesión confirma pendientes; cola no persistida. Solo una foto en memoria. No transferir nada automáticamente al firmware.
- `BatchSession.advance` solo tras guardado correcto o salto explícito. Guardado usa nombres exclusivos (`xb`) para los dos archivos; nunca sobrescribir en lote. Si falla la siguiente imagen, limpiar `source` para no exportar la anterior. `batch_busy` y desbloqueo diferido protegen doble clic. Preview con debounce 100 ms y caché por imagen/recorte/ajustes; exportar recalcula si cambió algo. `epd_bytes` y `write_epd` comparten serialización INK3 sin cambiar el formato.

Formato `.epd`:

```text
INK3
version uint8 = 1
width uint16 little endian
height uint16 little endian
black plane, 1 bit/pixel, MSB first
red plane, 1 bit/pixel, MSB first
```

Un píxel blanco no está activo en ninguno de los dos planos.

Funciones relevantes:

- `Foto2InkApp`: UI y flujo.
- `quantize_clean`: modo de manchas limpias.
- `quantize_detailed`: dithering/detalle.
- `pack_plane`: empaquetado de bits.
- `write_epd`: cabecera y planos.

Si cambias el formato, actualiza simultáneamente `foto2ink/README.md`, el README raíz y cualquier firmware consumidor. No uses herramientas de imagen para reescribir el algoritmo si una edición directa del código es suficiente.

## Compilación

Versiones:

- Gradle 9.4.1.
- AGP 8.13.2.
- Kotlin 2.2.10.
- Compose BOM 2026.02.01.
- JDK del daemon 21.
- source/target compatibility Java 11.
- min SDK 26; compile/target SDK 36.

Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat connectedAndroidTest
```

Linux/macOS:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew connectedAndroidTest
```

Si `JAVA_HOME` no está configurado en Windows y existe Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

Salida debug esperada: `app/build/outputs/apk/debug/app-debug.apk`.

Para `foto2ink`:

```powershell
cd foto2ink
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python foto2ink.py
```

No abras la GUI durante verificaciones automatizadas. Usa `python -B` para no producir caches innecesarios. Para cambios de algoritmo, añade o ejecuta pruebas puras sobre las funciones de cuantización/empaquetado.

## Verificación según el cambio

- Solo documentación: revisar enlaces/rutas, comandos y `git diff --check`.
- Modelo/estado/repositorio: `test` + `assembleDebug`; añadir pruebas unitarias relevantes.
- UI Compose: `assembleDebug`, previews si sirven, inspección en emulador y test instrumentado cuando corresponda.
- Manifiesto/permisos/receivers: `assembleDebug`, `lint` y prueba en dispositivo.
- Dependencias/Gradle: sincronización limpia, `test`, `lint` y `assembleDebug`.
- `foto2ink`: importar/compilar Python, probar funciones afectadas y hacer una exportación manual si cambia la UI o el formato.
- Cambio transversal o pre-release: `test`, `lint`, `assembleDebug` y `connectedAndroidTest` con dispositivo disponible.

No afirmes que una prueba pasó si el entorno no pudo ejecutarla. Documenta el bloqueo exacto (por ejemplo, JDK, SDK, red o ausencia de emulador).

## Convenciones y reglas de edición

- Mantén Kotlin con estilo oficial y formateo coherente con el archivo.
- Mantén la UI en Compose; no introduzcas layouts XML salvo una decisión explícita de arquitectura.
- Mantén textos de usuario y documentación en español, salvo nombres técnicos o compatibilidad existente.
- Guarda texto como UTF-8. Hay tildes y `ñ`; evita introducir mojibake.
- Prefiere estado inmutable (`copy`) y mutaciones centralizadas en `CalendarState`.
- No escribas directamente en SharedPreferences desde vistas nuevas si puede encapsularse en repositorio/estado. Las preferencias visuales existentes en `CalendarApp` son deuda técnica, no un patrón a expandir.
- Conserva la separación entre `CalendarEvent` persistido, `CalendarOccurrence` calculado y `EventDraft` editable.
- Al añadir una categoría/recurrencia/recordatorio, revisa serialización, UI, etiquetas, filtros, payload, tutorial y datos por defecto.
- Al añadir campos persistidos, aplica la lista de compatibilidad de la sección Persistencia.
- No añadas nuevas acciones de red ni permisos fuera de la integración local e-paper y la lectura opcional CalendarContract sin justificar y documentar su necesidad.
- Mantén el cliente fuera del hilo principal y no guardes la contraseña Wi-Fi en preferencias/logs.
- No cambies el formato `.epd` de forma incompatible sin versionarlo.
- Mantén accesibilidad: `contentDescription` para iconos accionables y contraste legible en las tres paletas.
- Los componentes internos usan con frecuencia `internal`; conserva el alcance mínimo útil.
- Evita duplicar lógica entre vistas; los componentes comunes pertenecen en `SharedUi.kt` cuando realmente son compartidos.
- Para strings nuevos de producto, considera migrarlos a recursos si se necesita localización; el estado actual tiene mucho texto inline.

## Higiene del repositorio

- No edites ni añadas salidas de `build/`, `.gradle/`, `.kotlin/`, caches del IDE o APKs.
- `local.properties` es específico de máquina y está ignorado.
- `.idea/vcs.xml` puede aparecer como cambio local no rastreado; no lo incluyas ni borres sin petición explícita.
- El `.pyc` histórico se retiró del índice por petición de limpieza Git; conservarlo localmente e ignorado. No volver a rastrear caches.
- Puede haber cambios del usuario en el árbol de trabajo. Revisa `git status --short`, preserva cambios ajenos y no uses `git reset --hard` ni checkout destructivo.
- Usa los wrappers del repositorio, no una instalación global de Gradle.
- No guardes secretos, rutas locales, keystores ni credenciales.
- No cambies la firma/publicación como efecto secundario de otra tarea.

## Deuda técnica y riesgos conocidos

- Cobertura del payload, cliente HTTP y calendarios importados; recordatorios y varios flujos de UI aún sin cobertura suficiente. No confundir compilar pruebas instrumentadas con ejecutarlas.
- `CalendarState` y `CalendarApp` concentran muchas responsabilidades.
- `AddEventPanel.kt` y varios archivos Compose son grandes.
- Persistencia de todos los eventos en un solo JSON de SharedPreferences.
- Sin esquema versionado ni recuperación diferenciada: un error de parseo vuelve a datos demo.
- Textos principalmente inline; localización incompleta.
- Backup/data extraction: importados excluidos; política de los datos locales aún pendiente de revisar.
- Variante release sin firma ni endurecimiento de producción.
- Alarmas inexactas y sin estrategia explícita para restricciones agresivas de batería.
- Límite de 520 iteraciones en recurrencias y ventana de dos años para recordatorios.
- Sin importación/exportación general de archivos ni sincronización directa multi-dispositivo o nube; la integración opcional lee la copia de calendarios del sistema.
- El protocolo e-paper local no tiene TLS/autenticación y depende de una red de confianza.
- Algunos metadatos IDE siguen rastreados; caches Python y binarios están excluidos de las nuevas versiones.

Si una tarea toca estas áreas, no hagas una refactorización amplia no solicitada. Señala el riesgo y realiza el cambio mínimo coherente, salvo que el usuario pida explícitamente resolver la deuda.

## Antes de entregar cambios

1. Revisa `git status --short` y separa mentalmente cambios previos del usuario.
2. Inspecciona el diff solo de los archivos modificados.
3. Ejecuta la verificación proporcional indicada arriba.
4. Ejecuta `git diff --check`.
5. Confirma que no se añadieron rutas locales, secretos, binarios o archivos generados.
6. Resume qué cambió, qué se verificó y cualquier limitación restante.
