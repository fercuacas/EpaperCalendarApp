# Ink Calendar · Control USB

Aplicación de escritorio Python/Tkinter, independiente de Android y de foto2ink. Controla Ink_calendar32 por USB, sin activar Wi-Fi. Incluye editor de contenido y una actualización de firmware explícita.

## Instalación y arranque

Desde la raíz de este repositorio:

```powershell
python -m pip install -r usb_calendar/requirements-build.txt
python -B -m usb_calendar.usb_calendar
```

Si solo se necesita controlar un firmware ya actualizado, basta `requirements.txt` (pyserial). Para compilar, PlatformIO debe tener instalados la plataforma, compilador y bibliotecas del proyecto; la primera preparación puede necesitar Internet **en el ordenador**, no Wi-Fi en el ESP32. No se instalan dependencias automáticamente desde la interfaz.

También se puede abrir `iniciar.pyw` con doble clic si Windows tiene Python/Tkinter asociado. Si no aparece ventana, ejecutar el comando anterior para ver el error. Modo de demostración: `python -B -m usb_calendar.usb_calendar --demo`. DEMO nunca abre un puerto ni permite flashear.

## Control del calendario

1. Conectar el USB de datos. Si el XIAO duerme, pulsar y soltar brevemente el central para que reaparezca el puerto.
2. Elegir el puerto y pulsar **Conectar**. Cerrar antes monitores serie de Arduino/PlatformIO: solo un programa puede usar el puerto.
3. En **Navegar**, escribir `AAAA-MM-DD` y **Mostrar fecha**, o usar ±1/±7 días. **Hoy (en pruebas)** toma la fecha del reloj del dispositivo.
4. Elegir Día, Semana, Mes o Personalizada y pulsar **Aplicar**.
5. En **Automático**, elegir dirección y espera de 1–86400 segundos; pulsar **Iniciar** y después **PARAR** para detenerlo. El intervalo empieza después de terminar cada refresco, no se acumulan órdenes ni se recuperan retrasos. PARAR no puede interrumpir un refresco físico que ya comenzó. Hay otro PARAR siempre visible en el pie.
6. **Volver al calendario normal** elimina la fecha de prueba. **Desconectar** intenta además liberar el reposo. Si se pierde el cable o se cierra a la fuerza, la sesión de pruebas caduca a los cinco minutos desde la última renovación. El XIAO vuelve al calendario y después aplica su reposo normal.

### Sincronizar fecha y hora reales

En **Navegar → Sincronizar fecha con el PC…**, revisar el reloj del ordenador y confirmar. Esta acción sí ajusta el reloj real del ESP32 (fecha y hora), guarda su checkpoint y cierra la fecha de prueba. No equivale a **Hoy (en pruebas)**, que solo muestra temporalmente el día que ya conoce el dispositivo.

- Debe existir un calendario válido en el ESP32. El botón requiere conexión y queda deshabilitado durante un refresco, avance automático o compilación/carga; pulsar PARAR y esperar si se estaba recorriendo días.
- Toma la hora del PC al ejecutar la orden y envía epoch UTC. La fecha/hora local usa la **zona horaria ya configurada en el calendario**, incluyendo los cambios de horario que soporte el firmware. No cambia esa zona ni consulta Internet; comprobar que el reloj del PC sea correcto.
- Verifica una respuesta correlacionada con el UTC aplicado, `clockSource=usb` y `clockNeedsSync=false`, espera al refresco y muestra el resultado en el pie. Ante timeout o respuesta inesperada no reintenta automáticamente ni anuncia éxito.
- Usa el comando `time` que ya existe en el firmware y el protocolo USB v1: no requiere recompilar ni cargar firmware si ya funciona el controlador. No cambia eventos, frases/fotos, credenciales ni activa Wi-Fi. En DEMO solo ajusta el simulador.
- Mantiene el dispositivo despierto mientras siga conectado el controlador. Al desconectar se libera el reposo normal. Un arranque tras pérdida total de alimentación puede requerir volver a sincronizar; no hay RTC externo con pila.

La conexión requiere `usbProtocolVersion=1`. Un firmware anterior produce un aviso; se puede actualizar desde la pestaña **Actualizar** aunque no permita conectar el controlador. No se reinicia deliberadamente la placa al abrir el monitor (DTR/RTS desactivados). La navegación de prueba no cambia RTC, eventos, preferencias, imágenes ni firmware; **Sincronizar fecha** es la excepción explícita que ajusta el RTC/checkpoint. La fecha de prueba es temporal; fuera del intervalo de eventos se muestra un día vacío, pero siguen funcionando frases y fotos. Durante el control, central corto cambia solo la vista temporal.

La monitorización muestra fecha real y mostrada, vista, reloj fiable o pendiente de sincronización, Wi-Fi/driver, botones, memoria, refrescos, frase/foto y catálogo. No hay medición de batería ni porcentajes inventados. Se puede guardar un diagnóstico JSON local; no se transmite a ningún servicio. No confundir la fecha solicitada mientras hay refresco pendiente con una imagen física ya terminada.

Al conectar se muestran las fases **Puerto abierto → Calendario detectado → Reposo suspendido → Listo**. Ver el puerto no implica que el calendario haya respondido. La negociación vuelve a consultar estado si la primera respuesta se pierde al despertar; la retención USB se confirma antes de habilitar controles y se renueva cada 30 s. Solo se reintentan consultas y esa retención idempotente, no saltos/fechas/ajustes de reloj. Durante un refresco los botones quedan grises y se habilitan al terminar, también en el primero. Desconectar cancela la negociación sin esperar todo el timeout; una pantalla ya refrescando puede necesitar terminar antes de cerrar. Si COM desaparece, despertar con el central y conectar de nuevo. No abrir dos controladores/monitores a la vez.

**Privacidad:** no subir diagnósticos (pueden contener frases, nombres de fotos y datos del calendario), fotos, `.epd`, reglas/frases ni firmware/APKs con el catálogo personal. Permanecen ignorados. La pestaña Actualizar guarda contenido solo en el ordenador; GitHub publica únicamente el código. Ver la política Git del README raíz.

## Editar contenido y compilar/cargar

1. Abrir **Actualizar** y seleccionar la **raíz de Ink_calendar32** (la que contiene `assets`, `tools` y `firmware`), no este repositorio Android ni solo `firmware`.
2. Elegir la placa correcta. XIAO es la predeterminada; el perfil debe conservar `INK_WIFI_DISABLED=1` mientras no tenga antena. Si el controlador detectó otra placa, no se acepta un perfil contradictorio.
3. Pulsar **Editar frases y fechas…**. Las frases normales se editan una por línea, en orden. En la segunda pestaña se edita el JSON completo de reglas: ancla, semilla, fechas de frases/fotos y grupo opcional de fotos. `02-14` significa 14 de febrero; `02-29` solo se aplica en años bisiestos. Cada excepción anual pausa únicamente su propia lista.
4. **Guardar y cerrar** valida ambos documentos y los nombres `.epd` antes de escribir. Conserva claves adicionales del JSON de frases. Si otro editor modificó los archivos desde que se abrieron, se rechaza el guardado para no sobrescribirlo. Cada archivo se sustituye atómicamente; si hay error al guardar el segundo, revisar ambos antes de reabrir. El editor nunca carga firmware por sí solo.
5. Las fotos se siguen preparando con foto2ink y guardando en `Ink_calendar32/assets/images/`. No se copian ni convierten fotos desde este editor. Al compilar entran los `.epd` válidos actuales, frases y reglas guardadas; no los PNG ni los cambios sin guardar en otro editor.
6. Elegir un **puerto real**, despertar el XIAO y pulsar **Compilar y cargar por USB…**. Revisar la confirmación de placa, puerto y proyecto.

La actualización:

- Detiene el avance automático, espera/cierra el controlador y libera el puerto.
- Entra en el cargador ROM mediante esptool, sin borrar datos. Así el ESP32 no se duerme durante la compilación, también con firmware antiguo. La e-paper conserva su imagen mientras el programa está detenido.
- Compila el perfil con PlatformIO en segundo plano. La ventana sigue respondiendo y muestra el registro.
- Comprueba versión del catálogo generado y binario, tamaño y partición app0. Si cambian los recursos durante la compilación, se detiene y hay que repetir.
- Opcionalmente regenera el catálogo PNG/JSON de Android. **Esto no compila ni instala el APK**: para actualizar la foto en el móvil hay que volver a compilar/instalar la app.
- Lee la tabla de particiones del dispositivo en `0x8000` y exige que sea idéntica a la compilada. **No modifica particiones, bootloader, NVS ni LittleFS**. No es una herramienta de primera instalación de una placa vacía ni de migración de particiones.
- Escribe solo `firmware.bin` en app0 (`0x10000`) y esptool verifica la escritura y reinicia. No ejecuta `erase_flash`, `erase_region`, `uploadfs` ni una carga completa. No desconectar USB durante esta fase.
- Al terminar no abre automáticamente el puerto: esperar al arranque/refresco y pulsar Conectar. Si ya durmió, pulsar brevemente el central.

Una compilación o comprobación fallida no provoca una carga del binario anterior. Si el proceso ya había entrado en ROM, puede quedar en BOOT; con una compilación fallida el programa anterior sigue intacto y se puede volver a intentar la carga o reiniciar manualmente. Una interrupción **durante** escritura puede requerir cargar de nuevo antes de arrancar. La verificación de esptool no demuestra por sí sola pantalla, botones o funcionamiento físico: comprobarlos después. El reinicio de una carga puede dejar el reloj pendiente de sincronización; no se corrige silenciosamente. Después de conectar, usar **Sincronizar fecha con el PC…** si es necesario.

## Pruebas

```powershell
python -B -m unittest usb_calendar.test_controller usb_calendar.test_firmware_update usb_calendar.test_ui_actions -v
```

Son pruebas headless con proyectos temporales, puertos y procesos simulados: validación/editor, conflictos, protocolo, pausa/STOP, sincronización explícita de reloj y salida de preview, confirmación/cancelación, UTC inválido, rechazos/timeout sin reintentos, liberación de COM, compilación fallida, cambios de recursos, particiones distintas y escritura exclusivamente en app0. No abren Tk, no envían Wi-Fi ni cargan una placa real. La compilación de firmware se valida aparte con los dos perfiles habituales. La prueba física de carga desde la interfaz sigue siendo una aceptación manual explícita.
