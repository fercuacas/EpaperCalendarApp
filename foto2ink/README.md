# foto2ink

Conversor visual para preparar stickers o fotos pequeñas para una pantalla e-paper tricolor.

Entrada:

- PNG, JPG, JPEG, BMP, WebP.

Salida:

- `*_preview.png`: imagen final cuadrada en blanco, negro y rojo.
- `*.epd`: binario propio con dos planos de 1 bit, uno negro y otro rojo.

## Instalar

```powershell
cd foto2ink
python -m pip install -r requirements.txt
```

## Ejecutar

```powershell
python foto2ink.py
```

## Uso

1. Pulsa `Abrir foto`.
2. Arrastra el cuadrado sobre la imagen para elegir el recorte.
3. Usa `Tamano recorte` para hacer el cuadrado mas grande o mas pequeno.
4. Activa `Borrar fondo` si quieres eliminar un fondo conectado a los bordes.
5. Ajusta `Potencia fondo` hasta que solo quede el sticker.
6. Elige el modo:
   - `Detallado`: intenta parecerse mas a la foto original usando dithering para simular grises y colores suaves.
   - `Sticker limpio`: reduce la imagen a manchas limpias de blanco, negro y rojo.
7. Ajusta `Dither / detalle` si usas el modo detallado. En `0`, el resultado es igual a `Sticker limpio`; en `100`, aplica el maximo detalle.
8. Ajusta `Salida cuadrada px` para que coincida exactamente con el layout donde se pintara.
9. Pulsa `Exportar`.

## Procesar muchas fotos seguidas

1. Pulsa **Procesar varias fotos…** y selecciona todas las imágenes que quieras en el selector de archivos (Ctrl o Mayús para selección múltiple).
2. Antes de abrir la primera, elige la **carpeta de salida** y el **tamaño inicial**, entre 64 y 240 px; puedes escribir un valor exacto como 124.
3. Pulsa **Empezar lote**. Verás `Foto 1/N`, el nombre y la carpeta de destino.
4. Arrastra el recorte, cambia su tamaño y ajusta fondo, modo y detalle como siempre. También puedes cambiar el tamaño de salida de esta foto.
5. Pulsa **Listo → siguiente** o **Ctrl+Enter**. Se guardan el `.epd` y el `_preview.png` y aparece la siguiente foto, sin diálogo de guardado ni aviso de éxito intermedio.
6. Repite hasta que aparezca **Lote terminado**. **Saltar foto** omite la actual sin exportarla.

El tamaño de salida, el porcentaje del recorte y los ajustes de conversión se mantienen para la siguiente foto. La posición del recorte vuelve al centro y se adapta a las dimensiones de cada imagen: no reutiliza las coordenadas de la anterior. Solo se carga una foto cada vez, aunque selecciones cientos.

Los nombres conservan el nombre de origen (`viaje.epd` y `viaje_preview.png`). Si cualquiera de los dos ya existe, se añade `_001`, `_002`, etc. Nunca se sobrescriben archivos en el modo por lotes, tampoco originales que coincidan con un nombre de preview. Si falla el guardado, se limpian los nuevos archivos incompletos y la foto sigue pendiente para reintentar o saltar. Una imagen que no se pueda abrir no exporta por error la foto anterior.

**Cerrar lote**, abrir otra foto o cerrar la aplicación pide confirmación si quedan fotos pendientes. Las ya guardadas permanecen; la cola y los ajustes del lote no se recuperan después de cerrar el programa. La carpeta elegida se recuerda solo durante la sesión. `Abrir foto` y `Exportar` conservan el flujo individual.

Esto prepara archivos locales; no los transfiere al ESP32. Para incorporarlos a `Ink_calendar32`, copia los `.epd` a sus recursos y recompila/carga el firmware según su documentación.

## Pruebas sin interfaz

Desde la raíz del repositorio:

```powershell
python -B -m unittest foto2ink.test_batch_export -v
```

Cubren cola grande sin cargar imágenes, recortes y ajustes independientes, exportación completa, nombres duplicados, protección de originales, errores/reintentos, doble clic y compatibilidad INK3. No abren ventanas. `-B` evita modificar el `.pyc` histórico rastreado.

## Consejos

- Para fotos/dibujos con lagrimas, sombras o brillos, usa `Detallado`.
- Si una zona sale demasiado sucia, baja `Dither / detalle`.
- Si una zona clara se vuelve negra, sube un poco el borrado de fondo o prueba con menos detalle.
- Para iconos muy simples o siluetas, usa `Sticker limpio`.

## Formato `.epd`

El archivo exportado usa este formato:

```text
magic: 4 bytes = INK3
version: uint8 = 1
width: uint16 little endian
height: uint16 little endian
blackPlane: width*height bits, MSB first
redPlane: width*height bits, MSB first
```

El blanco no se guarda como plano propio: un pixel es blanco cuando no esta activo ni en negro ni en rojo.
