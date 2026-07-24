# foto2ink

Conversor visual para preparar stickers o fotos pequeñas para una pantalla e-paper tricolor.

Entrada:

- PNG, JPG, JPEG, BMP, WebP.

Salida:

- `*_preview.png`: imagen final cuadrada en blanco, negro y rojo.
- `*.epd`: binario propio con dos planos de 1 bit, uno negro y otro rojo.

## Instalar

```powershell
cd C:\Users\ferna\AndroidStudioProjects\EpaperCalendar\foto2ink
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
