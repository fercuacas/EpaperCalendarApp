# E-paper Calendar

Aplicación Android de calendario personal, escrita en Kotlin y Jetpack Compose. Permite organizar eventos y tareas, consultar la agenda por día, semana o mes, programar recordatorios y sincronizar por Wi-Fi un calendario de 13 meses con el proyecto `Ink_calendar32`.

También incluye [foto2ink](foto2ink/README.md) y el [controlador de escritorio USB](usb_calendar/README.md). Este último permite probar fechas/vistas, recorrer días automáticamente, monitorizar el ESP32 y **editar frases/fechas y compilar/cargar el firmware por USB**, sin activar Wi-Fi. Su pestaña **Actualizar** pide la carpeta del proyecto `Ink_calendar32`, perfil y puerto, valida el contenido, comprueba particiones y escribe solo app0, sin borrar eventos/ajustes. La regeneración opcional del catálogo Android no compila ni instala el APK. Ver la guía antes de la primera actualización.

El controlador USB también ofrece **Navegar → Sincronizar fecha con el PC…**: tras confirmación ajusta el reloj real con la fecha/hora del ordenador y sale de la fecha de prueba, sin Wi-Fi ni cambios en los eventos. No confundirlo con **Hoy (en pruebas)**; el ajuste no es automático al conectar ni al cargar firmware.

## Privacidad de fotos y frases

GitHub contiene el código y documentación, **no el contenido personal**. Están ignorados el catálogo `app/src/main/assets/personal_content/` (incluye frases, fechas y PNG en base64), fotos originales, `.epd`, previews, diagnósticos y binarios/APK que podrían incrustarlos. El contenido local no se borra al confirmar cambios. No usar `git add -f`, adjuntar APKs/firmware privados a releases ni copiar el catálogo a documentación o pruebas.

En un clon nuevo, preparar primero los recursos locales de `Ink_calendar32` según su README; se puede empezar con sus plantillas vacías `examples/*.example.json`. Después exportar el catálogo con `python -B tools/sync_personal_content.py --ink-root "<carpeta Ink_calendar32>"` y compilar Android. Un catálogo vacío no incluye fotos personales: se muestra el placeholder. El catálogo se empaqueta en el APK local; no es cifrado y no debe distribuirse si contiene datos privados. Mantener copias de seguridad propias fuera de Git de las fotos, frases y reglas; Git no las recuperará.

Antes de cada commit/push, ejecutar `python -B tools/check_public_tree.py` y revisar `git diff --cached`. La comprobación detecta rutas privadas/generadas en el índice actual, no secretos incrustados arbitrariamente en código ni el historial. `.gitignore` no elimina archivos de commits antiguos: esta publicación **no reescribe el historial**; lo anteriormente publicado puede seguir accesible allí.

El repositorio también incluye `foto2ink`, una herramienta de escritorio en Python para convertir imágenes a blanco, negro y rojo y exportarlas al formato binario `.epd`.

## Estado y alcance

La aplicación funciona como calendario local, con lectura opcional de los calendarios del móvil:

- Los eventos y las preferencias se guardan en el almacenamiento privado de la aplicación.
- Puede leer Google Calendar, Outlook y otros calendarios que Android ya haya sincronizado, con tu permiso. Nunca los modifica.
- No necesita una cuenta ni servidor remoto; la red solo se usa para hablar directamente con el ESP32 en la LAN o en su AP.
- La opción e-paper descubre el dispositivo, configura su Wi-Fi, previsualiza el JSON y lo sincroniza por HTTP local.
- `foto2ink` es una utilidad independiente; no está conectada automáticamente con la aplicación Android.

## Funciones principales

- Agenda diaria con eventos por hora, eventos de todo el día y tareas.
- Vistas de semana y mes.
- Navegación rápida entre fechas y meses.
- Creación, edición, duplicado, desplazamiento y eliminación de eventos.
- Eventos de un día, de todo el día y de varios días.
- Repetición diaria, semanal, mensual o cada cierto número de días.
- Edición de una sola aparición, de esta y las siguientes, o de toda una serie recurrente.
- Recordatorios 5 minutos antes, 30 minutos antes o el día anterior.
- Reprogramación de recordatorios recurrentes y restauración tras reiniciar el dispositivo.
- Categorías: Trabajo, Focus time, Café, Fitness, Personal y Tarea.
- Marcado de tareas como completadas.
- Búsqueda sin distinguir mayúsculas ni acentos.
- Filtros por categoría, hoy o esta semana.
- Plantillas rápidas para Reunión, Gym, Café, Tarea y Viaje.
- Sugerencias sencillas a partir de texto natural en español, por ejemplo `Gym mañana 18:30`.
- Tutorial guiado con datos temporales que no modifica los eventos reales.
- Tres apariencias: cálida, blanco/azul y rosa/negro.
- Generación y envío del contrato e-paper v2 con recurrencias expandidas para 13 meses.

## Requisitos

Para compilar la aplicación Android:

- Android Studio con soporte para Android SDK 36.
- JDK 21 para ejecutar Gradle. Android Studio incluye normalmente un JDK compatible en su JetBrains Runtime.
- Android SDK Platform 36 y las herramientas de compilación que solicite Gradle.
- Un dispositivo o emulador con Android 8.0 (API 26) o posterior.
- Conexión a Internet durante la primera sincronización para descargar Gradle y las dependencias.

Versiones principales del proyecto:

| Componente | Versión/configuración |
| --- | --- |
| Gradle Wrapper | 9.4.1 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| `compileSdk` / `targetSdk` | 36 |
| `minSdk` | 26 |
| JVM de Gradle | 21 |
| Compatibilidad del código Android | Java 11 |

No es necesario instalar Gradle globalmente: el repositorio contiene `gradlew` y `gradlew.bat`.

## Obtener y abrir el proyecto

```bash
git clone https://github.com/fercuacas/EpaperCalendarApp.git
cd EpaperCalendarApp
```

En Android Studio:

1. Selecciona **Open** y abre la carpeta raíz del repositorio.
2. Espera a que termine la sincronización de Gradle.
3. Si Android Studio lo solicita, instala Android SDK Platform 36.
4. En la configuración de Gradle, selecciona el JDK integrado de Android Studio o cualquier JDK 21.
5. Crea o inicia un emulador con API 26 o superior, o conecta un dispositivo con depuración USB.
6. Selecciona la configuración `app` y pulsa **Run**.

Android Studio crea localmente `local.properties` con la ruta del SDK. Este archivo depende de cada máquina y no debe añadirse a Git.

## Compilar desde la terminal

En Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

En Linux o macOS:

```bash
./gradlew assembleDebug
```

Si Gradle no encuentra Java, configura `JAVA_HOME` para que apunte a un JDK 21. En una instalación habitual de Android Studio para Windows puede usarse temporalmente:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

El APK de depuración se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Puede instalarse con Android Studio o mediante ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La variante `release` no tiene todavía una configuración de firma ni un proceso de publicación. Antes de distribuirla hay que añadir una firma segura, revisar las reglas de optimización y proteger las credenciales fuera del repositorio.

## Pruebas y comprobaciones

Pruebas unitarias locales:

```powershell
.\gradlew.bat test
```

Pruebas instrumentadas, con un emulador o dispositivo conectado:

```powershell
.\gradlew.bat connectedAndroidTest
```

Análisis estático de Android:

```powershell
.\gradlew.bat lint
```

En Linux o macOS sustituye `.\gradlew.bat` por `./gradlew`.

Hay pruebas unitarias del payload y HTTP, conversión de calendarios del móvil, caché, permisos, actualización, aislamiento y protección de eventos importados. También hay pruebas instrumentadas del menú, consulta de eventos y persistencia compatible. La integración con la cuenta real, el proveedor de cada fabricante y la pantalla física requiere pruebas en dispositivo; los recordatorios locales y otros flujos aún necesitan más cobertura.

## Cómo usar la aplicación

### Calendarios del móvil (Google y otros proveedores)

1. Añade tu cuenta al móvil y activa su sincronización de calendarios en Android. Comprueba que sus eventos aparecen en la aplicación de calendario original.
2. En E-paper Calendar abre el **menú de los tres palitos > Calendarios del móvil > Elegir calendarios**.
3. Pulsa **Permitir acceso**. Solo se solicita `READ_CALENDAR`; no se pide escritura ni contraseña de Google.
4. Marca los calendarios que quieras mostrar, agrupados por cuenta. No se selecciona ninguno automáticamente. Cada uno usa Personal inicialmente; puedes elegir Trabajo, Focus, Café, Fitness o Personal.
5. Pulsa **Guardar y actualizar**. Los eventos aparecen junto a los locales, con su calendario de origen y una ficha de solo lectura. Para editarlos o borrarlos, usa la aplicación original.
6. Usa **Actualizar ahora** para releer manualmente. También se actualizan al abrir/reanudar la app, al cambiar de mes y al detectar cambios del proveedor mientras la app está visible. No hay actualización periódica en segundo plano.
7. **Desconectar calendarios** elimina la selección y la copia importada, previa confirmación. No elimina eventos del móvil, de Google ni eventos locales de esta app.

**Actualizar no fuerza una descarga de Google**: lee la copia local que Android ya tiene. El móvil debe sincronizar con Google por Internet antes de conectarse al AP sin Internet del ESP32. No hay OAuth, backend ni acceso directo a Google Calendar API.

Se leen 13 meses alrededor del mes seleccionado, igual que el payload e-paper. Android expande las recurrencias y sus excepciones; se preservan eventos de todo el día, intervalos de varios días y zonas horarias. No se duplican los avisos del calendario original.

Antes de previsualizar JSON o enviar al e-paper se vuelve a leer ese rango y se espera el resultado. Si falla o falta permiso, no se envía una copia potencialmente antigua. Los límites conjuntos siguen siendo 768 apariciones y 96 KiB, sin truncado. La actualización automática nunca envía a la pantalla ni enciende su Wi-Fi.

Ante errores se mantiene la última copia con aviso. Si revocas el permiso, se elimina la copia importada y se conserva solo la selección técnica para recuperarla al autorizar de nuevo. Si Android deja de preguntar por el permiso, abre **Permisos de la app > Calendario** desde el enlace de los ajustes.

La copia y configuración se guardan separadamente en `device_calendar_import` (JSON v1), excluido del backup y de la transferencia a otro dispositivo. El almacén local `epaper_calendar` no cambia de formato. El tutorial no consulta calendarios reales ni pide este permiso.

### Primer inicio

La primera vez aparece una bienvenida. Se puede entrar directamente o iniciar el tutorial de Robofer. El tutorial crea un estado de calendario separado y temporal: sus eventos, filtros y cambios no se guardan.

Si el dispositivo usa Android 13 o posterior, la aplicación ofrece activar las notificaciones al terminar la bienvenida. Sin ese permiso los eventos se guardan normalmente, pero no se muestran avisos.

Cuando aún no existen datos guardados, se crean varios eventos de demostración. Para volver al estado inicial hay que borrar los datos de la aplicación o desinstalarla y volver a instalarla.

### Navegación

Robofer, el botón flotante inferior, abre el selector de secciones:

- **Agenda:** frase y contador de tareas pendientes del día seleccionado, eventos de todo el día y cuadrícula de las 24 horas. Se abre a las 08:00; desliza dentro de la cuadrícula para llegar a las 00:00 o al final del día. Los bloques respetan su horario y duración, se distribuyen en paralelo al coincidir y ocupan todo el ancho cuando están solos. Los eventos muy cortos tienen un mínimo visual de 15 minutos para poder pulsarlos; su horario real no cambia.
- **Semana:** cuadrícula continua de las 24 horas, con la misma altura para cada hora y scroll propio que se abre a las 08:00. Los bloques respetan el inicio y fin; los eventos con el mismo inicio muestran un contador y franjas que terminan en su hora individual. Los solapamientos con distinto inicio aparecen en paralelo con abreviaturas al comienzo. Tocar un contador abre la agenda del día; los eventos importados individuales conservan su ficha de consulta. Todo el día y varios días permanecen separados.
- **Tareas:** lista de eventos de la categoría Tarea, con opción de completarlos.
- **Mes:** cuadrícula mensual y resumen del día seleccionado.

La barra superior permite elegir mes y acceder al menú; la búsqueda solo aparece en Semana y Mes. Las flechas de las cabeceras de Semana y Mes avanzan o retroceden un periodo y actualizan la fecha seleccionada. La semana conserva el día de la semana; al cambiar de mes se conserva el número de día cuando existe y, si no, se usa el último día de destino (31 de enero → 28/29 de febrero).

El tutorial respeta el espacio de la barra de estado, cámara y navegación del móvil. Sus tarjetas se colocan por encima o debajo de la zona resaltada cuando hay espacio; en pantallas pequeñas o con letra grande, la explicación se puede desplazar dentro de la tarjeta y los botones permanecen visibles. Los datos del tutorial siguen siendo temporales.

### Crear y editar eventos

Desde el botón de creación o una plantilla rápida se abre el editor. El título es obligatorio. Para eventos con hora, la hora final debe ser posterior a la inicial cuando comienzan y terminan el mismo día.

El editor admite:

- fecha inicial y final;
- hora inicial y final;
- evento de todo el día o de varios días;
- categoría;
- personas;
- ubicación o enlace;
- notas;
- recordatorio;
- regla y fin de repetición.

Al editar una serie recurrente se puede aplicar el cambio a una aparición, a esa aparición y las futuras, o a toda la serie. También se puede duplicar el evento, moverlo a mañana o borrarlo.

En **Más opciones > Repetición > Personalizado** puedes elegir **Días de la semana** y marcar varios días (por ejemplo lunes, miércoles y viernes). Se repite cada semana en esos días a partir de la fecha inicial; hay que seleccionar al menos uno. La opción **Cada X días** sigue disponible y los eventos antiguos conservan su intervalo al abrirlos.

Al pulsar **Borrar** en un evento repetido aparece una confirmación:

- **Solo este día:** omite únicamente la aparición que abriste y mantiene el resto de la serie. Si dura varios días, se elimina esa aparición completa y el diálogo indica su intervalo.
- **Toda la serie:** elimina todas las apariciones de ese evento, pasadas y futuras; no elimina eventos independientes.
- **Cancelar:** no cambia nada.

La excepción queda guardada, se respeta al reabrir la app, al calcular recordatorios y al enviar al e-paper. Estas opciones son para eventos locales; los calendarios importados del móvil siguen siendo de solo lectura.

### Búsqueda y filtros

La búsqueda y los filtros solo están disponibles y se aplican en **Semana y Mes**. Agenda y Tareas muestran sus eventos sin filtros ocultos, aunque antes se haya filtrado otra vista. Al regresar a Semana/Mes se recupera la selección de filtros. La búsqueda consulta el título, ubicación, personas, notas y categoría. Los acentos y las mayúsculas no afectan a la coincidencia. Los filtros de fecha y categoría se pueden combinar y permanecen guardados entre sesiones; el texto de búsqueda no se conserva al cerrar la aplicación.

### Recordatorios

Los recordatorios utilizan alarmas locales de Android y notificaciones:

- `POST_NOTIFICATIONS` se solicita en Android 13 o posterior.
- `RECEIVE_BOOT_COMPLETED` permite volver a programar los avisos tras reiniciar.
- Se programa la próxima aparición futura de cada evento recurrente; cuando se recibe, se prepara la siguiente.
- Se usa una alarma no exacta, por lo que Android puede retrasarla para ahorrar batería.

Si los avisos no aparecen, comprueba el permiso de notificaciones, el canal **Recordatorios** y las restricciones de batería del sistema.

### Datos y privacidad

Los eventos, la sección seleccionada, los filtros, el tema y el estado de bienvenida se guardan en `SharedPreferences`, dentro del espacio privado de la aplicación, con el nombre `epaper_calendar`.

El proyecto incluye un cliente HTTP exclusivamente para el ESP32 en la red local; no incluye autenticación, analítica ni sincronización en la nube. Android tiene habilitada la copia de seguridad de la aplicación y las reglas incluidas son todavía las plantillas predeterminadas; el comportamiento final de copia o transferencia depende del sistema y debería revisarse antes de publicar.

Al borrar los datos de la aplicación se eliminan los eventos y preferencias locales. No hay por ahora exportación/importación general ni migración a un calendario externo.

## Integración e-paper

En **Menú > Dispositivo e-paper > Configurar y sincronizar** se puede buscar `inkcalendar` mediante Android NSD, indicar un host/IP manual o usar `192.168.4.1` mientras el teléfono está conectado al AP `InkCalendar32`.

Primer arranque:

1. Alimenta y flashea el XIAO por USB-C. No conectes batería en esta fase.
2. Conecta el móvil a `InkCalendar32`, contraseña `inkcalendar`.
3. En la pantalla e-paper de la app pulsa **Usar AP**.
4. Escribe el SSID y contraseña de tu red doméstica y pulsa **Configurar Wi-Fi del ESP32**. La contraseña se transmite al ESP32 y se borra del estado de la app; no se guarda en Android.
5. Vuelve a conectar el móvil a la red doméstica, abre de nuevo el diálogo y pulsa **Buscar**.
6. Mantén pulsado el botón central del dispositivo si necesitas reabrir la ventana de sincronización y pulsa **Sincronizar ahora**.

La app envía todas las apariciones, sin aplicar los filtros de la interfaz, desde el primer día de seis meses antes hasta el último día de seis meses después del mes seleccionado. Las recurrencias se expanden en Android y las tareas completadas se omiten. Los límites son 768 apariciones y 96 KiB; si se superan, la app no envía nada y el dispositivo conserva su calendario anterior.

El protocolo es HTTP sin TLS ni autenticación y debe utilizarse únicamente en una red de confianza. `android:usesCleartextTraffic` está habilitado expresamente para esta comunicación local.

### Frase y foto del día seleccionado

El panel de Agenda muestra la frase y la previsualización PNG de la foto **del día seleccionado en la agenda**. La frase sustituye al saludo y debajo aparece el contador de tareas pendientes, sin comillas ni otro bloque de texto. Al tocar otro día en la tira semanal cambian ambas; al regresar se recupera exactamente el mismo contenido. Usa las mismas excepciones anuales, pausas y rotaciones que el e-paper. Para comparar, consultar en ambos la misma fecha y tener el mismo catálogo instalado. No necesita red; navegar no modifica el reloj ni envía nada al dispositivo.

El panel tiene márgenes uniformes y esquinas redondeadas. La foto ocupa un cuadrado de 104 dp a la derecha, centrado en su marco blanco y redondeado, sin deformarse ni tocar los bordes del panel. A la izquierda se muestra la frase completa y justo debajo el contador; con letra grande el contenido crece en altura, sin solaparse con la imagen. El tutorial usa únicamente frase/imagen de ejemplo, sin leer el catálogo personal. El catálogo y las imágenes se leen/decodifican fuera del hilo principal y una decodificación anterior nunca se etiqueta como la foto de una fecha nueva.

La fuente de verdad está en `Ink_calendar32`: frases normales en `assets/phrases/love_phrases.json`, imágenes INK3 en `assets/images/` y reglas en `assets/personal_content.json`. Las reglas permiten frases/fotos fijas cada año por fecha `MM-DD` (mes-día). Esos días pausan solo su lista; las frases normales van en orden y las fotos se barajan por vueltas sin repeticiones dentro de una vuelta. El 29 de febrero solo existe en años bisiestos. Para añadir fechas, usar el ejemplo documentado en `firmware/PERSONAL_CONTENT.md` del firmware; aquí no se editan reglas ni fotos.

Tras modificar recursos, desde la raíz Android:

```powershell
python -B tools/sync_personal_content.py --ink-root "<carpeta de Ink_calendar32>"
.\gradlew.bat assembleDebug
```

Instalar el APK y cargar el firmware compilado desde esa misma versión de los recursos. El comando exporta el catálogo portátil `app/src/main/assets/personal_content/catalog.json`, con previews PNG lossless obtenidas de los planos `.epd`, no de posibles PNG antiguos. Es contenido fuente empaquetado en el APK, sin rutas de máquina ni conexión al ordenador durante el uso. No editarlo a mano ni actualizar solo uno de los dispositivos; exportar cuando ya no se estén añadiendo fotos. El paquete tiene el mismo `assetsVersion` que el firmware. **Probar** y **Sincronizar ahora** avisan si no coincide o si el reloj del e-paper tiene otra fecha; enviar eventos no actualiza imágenes ni frases. No es transferencia de fotos por Wi-Fi ni sincronización remota automática.

Pruebas: `PersonalContentTest` verifica selección compartida, excepciones, pausas, años bisiestos, barajado y golden checks del paquete. `PersonalImageInstrumentedTest` verifica decodificación PNG, navegación real de AgendaSection (ida/vuelta y cambios rápidos), frase/foto coherentes, aislamiento del tutorial y geometría del panel en las tres paletas, a 320 dp y con letra hasta 1,6×.

### Contrato v2

```json
{
  "version": 2,
  "generatedAt": "2026-09-19T12:00:00+02:00[Europe/Madrid]",
  "timezone": "Europe/Madrid",
  "today": "2026-09-19",
  "selectedDate": "2026-09-19",
  "rangeStart": "2026-03-01",
  "rangeEnd": "2027-03-31",
  "calendarName": "Mi calendario",
  "quote": "Cada dia cuenta",
  "events": [
    {
      "occurrenceId": "id-serie@2026-09-19",
      "date": "2026-09-19",
      "endDate": "2026-09-19",
      "time": "09:30",
      "end": "10:30",
      "title": "Reunion",
      "category": "WORK",
      "allDay": false,
      "done": false
    }
  ]
}
```

Los textos visibles se transliteran a ASCII porque las fuentes actuales del firmware no cubren UTF-8 completo. Recordatorios, personas, notas y reglas de recurrencia no viajan: no forman parte de lo que representa la pantalla.

## Herramienta `foto2ink`

`foto2ink` es una interfaz gráfica de escritorio para preparar imágenes cuadradas para una pantalla e-paper tricolor.

Requisitos:

- Python 3.10 o posterior recomendado.
- Tkinter, normalmente incluido con Python en Windows y macOS; en algunas distribuciones Linux debe instalarse como paquete del sistema.
- Pillow 10.4 o posterior.

Instalación y ejecución:

```powershell
cd foto2ink
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python foto2ink.py
```

En Linux o macOS, la activación del entorno es:

```bash
source .venv/bin/activate
```

La herramienta acepta PNG, JPG/JPEG, BMP y WebP. Permite elegir un recorte cuadrado, borrar el fondo conectado a los bordes, ajustar el nivel de detalle y escoger entre los modos **Detallado** y **Sticker limpio**. El tamaño de salida puede configurarse entre 64 y 240 píxeles; el valor inicial es 160.

Para preparar muchas imágenes, pulsa **Procesar varias fotos…**, selecciona el conjunto y elige carpeta de salida y tamaño antes de empezar. Ajusta cada recorte y pulsa **Listo → siguiente** (o **Ctrl+Enter**) para guardar y cargar la siguiente sin más diálogos. Conserva los ajustes de conversión entre fotos, ofrece **Saltar foto**, muestra progreso y evita sobrescribir nombres existentes. La cola se mantiene solo mientras el programa está abierto; los archivos ya exportados permanecen. Sigue disponible el modo individual. Consulta [el flujo por lotes y sus pruebas](foto2ink/README.md).

Cada exportación crea:

- `nombre_preview.png`: vista final en blanco, negro y rojo.
- `nombre.epd`: dos planos de un bit, uno negro y otro rojo.

Formato binario `.epd`:

```text
magic: 4 bytes = INK3
version: uint8 = 1
width: uint16 little endian
height: uint16 little endian
blackPlane: width*height bits, MSB first
redPlane: width*height bits, MSB first
```

El blanco no tiene un plano propio: un píxel es blanco cuando no está activo en el plano negro ni en el rojo. Hay instrucciones de uso más detalladas en [`foto2ink/README.md`](foto2ink/README.md).

## Arquitectura y estructura

El proyecto Android usa una arquitectura local sencilla: las pantallas Compose llaman a `CalendarState`; este aplica reglas, actualiza el estado observable, persiste mediante `CalendarRepository` y delega los avisos en `CalendarReminderScheduler`.

```text
EpaperCalendar/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/fernando/epapercalendar/
│       │   │   ├── MainActivity.kt
│       │   │   ├── calendar/
│       │   │   │   ├── CalendarEvent.kt
│       │   │   │   ├── CalendarRepository.kt
│       │   │   │   ├── CalendarState.kt
│       │   │   │   └── CalendarReminder*.kt
│       │   │   └── ui/
│       │   │       ├── CalendarApp.kt
│       │   │       ├── AddEventPanel.kt
│       │   │       ├── AgendaView.kt
│       │   │       ├── WeekView.kt
│       │   │       ├── MonthView.kt
│       │   │       ├── Filters.kt
│       │   │       ├── ThemePicker.kt
│       │   │       ├── RoboferButton.kt
│       │   │       ├── Tutorial*.kt
│       │   │       ├── SharedUi.kt
│       │   │       └── theme/
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── foto2ink/
│   ├── foto2ink.py
│   ├── requirements.txt
│   └── README.md
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── AGENTS.md
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

### Componentes Android

- `MainActivity.kt`: actividad única y entrada a Compose.
- `CalendarApp.kt`: composición raíz, navegación, temas, bienvenida, permisos y coordinación de diálogos.
- `CalendarEvent.kt`: modelo de evento, apariciones recurrentes, categorías, recordatorios y borradores.
- `CalendarState.kt`: estado observable y reglas de negocio para guardar, filtrar, repetir, completar y generar el payload.
- `epaper/EpaperPayload.kt`: expansión de recurrencias, límites y serialización del contrato v2.
- `epaper/EpaperHttpClient.kt`: `GET /status`, `POST /config/wifi` y `POST /calendar` fuera del hilo principal.
- `epaper/EpaperNsdDiscovery.kt`: descubrimiento local de `_inkcalendar._tcp`.
- `ui/EpaperSetupDialog.kt`: configuración, búsqueda, prueba, previsualización y sincronización.
- `CalendarRepository.kt`: serialización JSON y persistencia en `SharedPreferences`.
- `CalendarReminderScheduler.kt`: programación y cancelación de alarmas.
- `CalendarReminderReceiver.kt`: publicación de notificaciones y encadenado de recurrencias.
- `CalendarReminderBootReceiver.kt`: restauración de alarmas tras reiniciar.
- `AddEventPanel.kt`: formulario, acciones de edición y parser de texto natural.
- `AgendaView.kt`, `WeekView.kt` y `MonthView.kt`: vistas principales.
- `TutorialOverlay.kt` y `TutorialTargets.kt`: secuencia guiada y registro de zonas destacadas.

## Limitaciones conocidas

- La lectura de Google y otros calendarios depende de la copia disponible en Android: no hay sincronización directa con la nube ni escritura en calendarios externos. Google Tasks no forma parte de esta integración.
- La sincronización e-paper requiere que el móvil y el ESP32 estén en la misma red, o que el móvil esté conectado al AP del ESP32.
- El transporte local no tiene TLS ni autenticación; no debe exponerse a Internet.
- Los eventos locales se almacenan como un JSON en `SharedPreferences`, sin esquema versionado; los calendarios importados tienen almacenamiento y versión propios.
- La generación de apariciones recurrentes tiene límites defensivos y no está pensada para calendarios masivos.
- Los recordatorios usan alarmas no exactas.
- No hay exportación/importación general de archivos ni sincronización multi-dispositivo.
- Hay pruebas unitarias del payload v2 y del cliente HTTP; la UI NSD y el panel físico requieren prueba manual/instrumentada.
- La configuración de copia de seguridad, firma, optimización y publicación necesita revisión antes de producción.

## Licencia

Este proyecto se distribuye bajo la licencia Apache 2.0. Consulta [`LICENSE`](LICENSE) para ver el texto completo.
