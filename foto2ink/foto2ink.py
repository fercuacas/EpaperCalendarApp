from __future__ import annotations

import math
import struct
from collections import deque
from dataclasses import dataclass
from pathlib import Path
from tkinter import (
    BooleanVar,
    Button,
    Checkbutton,
    Canvas,
    Frame,
    IntVar,
    Label,
    OptionMenu,
    Scale,
    StringVar,
    Tk,
    filedialog,
    messagebox,
)
from tkinter import HORIZONTAL, LEFT, RIGHT, TOP, BOTH, X, Y

from PIL import Image, ImageEnhance, ImageOps, ImageTk


DEFAULT_OUTPUT_SIZE = 160
CANVAS_SIZE = 520
INK_WHITE = (255, 255, 255)
INK_BLACK = (0, 0, 0)
INK_RED = (196, 24, 32)


@dataclass
class CropBox:
    x: float
    y: float
    size: float


class Foto2InkApp:
    def __init__(self) -> None:
        self.root = Tk()
        self.root.title("foto2ink - conversor e-paper")
        self.root.minsize(940, 650)

        self.source_path: Path | None = None
        self.source: Image.Image | None = None
        self.display_image: Image.Image | None = None
        self.display_photo: ImageTk.PhotoImage | None = None
        self.preview_photo: ImageTk.PhotoImage | None = None
        self.crop = CropBox(0, 0, 1)
        self.scale_to_display = 1.0
        self.display_offset = (0, 0)
        self.drag_start: tuple[int, int] | None = None
        self.drag_crop_start: tuple[float, float] | None = None

        self.status = StringVar(value="Abre una foto para empezar.")
        self.remove_bg = BooleanVar(value=False)
        self.crop_percent = StringVar(value="80")
        self.render_mode = StringVar(value="Detallado")
        self.output_size = IntVar(value=DEFAULT_OUTPUT_SIZE)

        self._build_ui()
        self._bind_events()

    def _build_ui(self) -> None:
        top = Frame(self.root, padx=12, pady=10)
        top.pack(side=TOP, fill=X)

        Button(top, text="Abrir foto", command=self.open_image, width=14).pack(side=LEFT)
        Button(top, text="Exportar", command=self.export_output, width=14).pack(side=LEFT, padx=(8, 0))
        Checkbutton(top, text="Borrar fondo", variable=self.remove_bg, command=self.update_preview).pack(side=LEFT, padx=(18, 0))

        Label(top, text="Potencia fondo").pack(side=LEFT, padx=(18, 4))
        self.bg_strength = Scale(top, from_=0, to=100, orient=HORIZONTAL, length=170, command=lambda _v: self.update_preview())
        self.bg_strength.set(45)
        self.bg_strength.pack(side=LEFT)

        Label(top, text="Tamano recorte").pack(side=LEFT, padx=(18, 4))
        self.crop_scale = Scale(top, from_=20, to=100, orient=HORIZONTAL, length=170, command=self.on_crop_size)
        self.crop_scale.set(80)
        self.crop_scale.pack(side=LEFT)

        main = Frame(self.root, padx=12, pady=6)
        main.pack(fill=BOTH, expand=True)

        left_panel = Frame(main)
        left_panel.pack(side=LEFT, fill=BOTH, expand=True)
        Label(left_panel, text="Recorte cuadrado").pack(anchor="w")
        self.canvas = Canvas(left_panel, width=CANVAS_SIZE, height=CANVAS_SIZE, bg="#f2f2f2", highlightthickness=1, highlightbackground="#bbbbbb")
        self.canvas.pack(fill=BOTH, expand=True)

        right_panel = Frame(main, padx=14)
        right_panel.pack(side=RIGHT, fill=Y)
        Label(right_panel, text="Preview e-paper").pack(anchor="w")
        self.preview_canvas = Canvas(right_panel, width=320, height=320, bg="#ffffff", highlightthickness=1, highlightbackground="#bbbbbb")
        self.preview_canvas.pack(pady=(6, 12))

        Label(right_panel, text="Salida cuadrada px").pack(anchor="w")
        self.output_scale = Scale(right_panel, from_=64, to=240, orient=HORIZONTAL, length=260, variable=self.output_size, command=lambda _v: self.update_preview())
        self.output_scale.set(DEFAULT_OUTPUT_SIZE)
        self.output_scale.pack(anchor="w", pady=(0, 10))

        Label(right_panel, text="Modo de conversion").pack(anchor="w")
        OptionMenu(right_panel, self.render_mode, "Detallado", "Sticker limpio", command=lambda _v: self.update_preview()).pack(fill=X, pady=(2, 10))

        Label(right_panel, text="Dither / detalle").pack(anchor="w")
        self.detail_strength = Scale(right_panel, from_=0, to=100, orient=HORIZONTAL, length=260, command=lambda _v: self.update_preview())
        self.detail_strength.set(70)
        self.detail_strength.pack(anchor="w", pady=(0, 12))

        help_text = (
            "Controles:\n"
            "- Arrastra el cuadrado para mover el recorte.\n"
            "- Usa la barra de tamano para cambiar el area.\n"
            "- Borrar fondo elimina zonas conectadas a los bordes.\n"
            "- Detallado simula grises con puntos.\n"
            "- Exportar crea PNG preview + binario .epd."
        )
        Label(right_panel, text=help_text, justify=LEFT, anchor="w").pack(fill=X)

        bottom = Frame(self.root, padx=12, pady=8)
        bottom.pack(side=TOP, fill=X)
        Label(bottom, textvariable=self.status, anchor="w").pack(side=LEFT, fill=X, expand=True)

    def _bind_events(self) -> None:
        self.canvas.bind("<ButtonPress-1>", self.on_mouse_down)
        self.canvas.bind("<B1-Motion>", self.on_mouse_drag)
        self.canvas.bind("<MouseWheel>", self.on_mouse_wheel)

    def run(self) -> None:
        self.root.mainloop()

    def open_image(self) -> None:
        path = filedialog.askopenfilename(
            title="Abrir foto",
            filetypes=[
                ("Imagenes", "*.png *.jpg *.jpeg *.bmp *.webp"),
                ("Todos los archivos", "*.*"),
            ],
        )
        if not path:
            return
        try:
            image = Image.open(path)
            image = ImageOps.exif_transpose(image).convert("RGB")
        except Exception as exc:
            messagebox.showerror("No se pudo abrir", f"No se pudo abrir la imagen:\n{exc}")
            return

        self.source_path = Path(path)
        self.source = image
        self._fit_image_to_canvas()
        side = min(image.size) * 0.8
        self.crop = CropBox((image.width - side) / 2, (image.height - side) / 2, side)
        self.crop_scale.set(80)
        self.status.set(f"Imagen abierta: {self.source_path.name}")
        self.redraw()
        self.update_preview()

    def _fit_image_to_canvas(self) -> None:
        if self.source is None:
            return
        max_w = CANVAS_SIZE
        max_h = CANVAS_SIZE
        scale = min(max_w / self.source.width, max_h / self.source.height)
        display_w = max(1, int(self.source.width * scale))
        display_h = max(1, int(self.source.height * scale))
        self.scale_to_display = scale
        self.display_offset = ((CANVAS_SIZE - display_w) // 2, (CANVAS_SIZE - display_h) // 2)
        self.display_image = self.source.resize((display_w, display_h), Image.Resampling.LANCZOS)
        self.display_photo = ImageTk.PhotoImage(self.display_image)

    def redraw(self) -> None:
        self.canvas.delete("all")
        if self.display_photo is None:
            self.canvas.create_text(CANVAS_SIZE // 2, CANVAS_SIZE // 2, text="Abre una foto", fill="#666666")
            return

        ox, oy = self.display_offset
        self.canvas.create_image(ox, oy, anchor="nw", image=self.display_photo)

        x1, y1, x2, y2 = self.crop_to_display_rect()
        self.canvas.create_rectangle(0, 0, CANVAS_SIZE, y1, fill="#000000", stipple="gray25", outline="")
        self.canvas.create_rectangle(0, y2, CANVAS_SIZE, CANVAS_SIZE, fill="#000000", stipple="gray25", outline="")
        self.canvas.create_rectangle(0, y1, x1, y2, fill="#000000", stipple="gray25", outline="")
        self.canvas.create_rectangle(x2, y1, CANVAS_SIZE, y2, fill="#000000", stipple="gray25", outline="")
        self.canvas.create_rectangle(x1, y1, x2, y2, outline="#c41820", width=3)
        self.canvas.create_rectangle(x1 + 5, y1 + 5, x2 - 5, y2 - 5, outline="#ffffff", width=1)

    def crop_to_display_rect(self) -> tuple[int, int, int, int]:
        ox, oy = self.display_offset
        s = self.scale_to_display
        x1 = int(ox + self.crop.x * s)
        y1 = int(oy + self.crop.y * s)
        x2 = int(ox + (self.crop.x + self.crop.size) * s)
        y2 = int(oy + (self.crop.y + self.crop.size) * s)
        return x1, y1, x2, y2

    def on_mouse_down(self, event) -> None:
        if self.source is None:
            return
        self.drag_start = (event.x, event.y)
        self.drag_crop_start = (self.crop.x, self.crop.y)

    def on_mouse_drag(self, event) -> None:
        if self.source is None or self.drag_start is None or self.drag_crop_start is None:
            return
        dx = (event.x - self.drag_start[0]) / self.scale_to_display
        dy = (event.y - self.drag_start[1]) / self.scale_to_display
        self.crop.x = self.drag_crop_start[0] + dx
        self.crop.y = self.drag_crop_start[1] + dy
        self._clamp_crop()
        self.redraw()
        self.update_preview()

    def on_mouse_wheel(self, event) -> None:
        if self.source is None:
            return
        delta = 3 if event.delta > 0 else -3
        new_value = max(20, min(100, self.crop_scale.get() + delta))
        self.crop_scale.set(new_value)
        self.on_crop_size(str(new_value))

    def on_crop_size(self, value: str) -> None:
        if self.source is None:
            return
        percent = float(value) / 100.0
        old_cx = self.crop.x + self.crop.size / 2
        old_cy = self.crop.y + self.crop.size / 2
        new_size = min(self.source.width, self.source.height) * percent
        self.crop = CropBox(old_cx - new_size / 2, old_cy - new_size / 2, new_size)
        self._clamp_crop()
        self.redraw()
        self.update_preview()

    def _clamp_crop(self) -> None:
        if self.source is None:
            return
        self.crop.size = max(10, min(self.crop.size, self.source.width, self.source.height))
        self.crop.x = max(0, min(self.crop.x, self.source.width - self.crop.size))
        self.crop.y = max(0, min(self.crop.y, self.source.height - self.crop.size))

    def make_ink_image(self) -> Image.Image | None:
        if self.source is None:
            return None
        box = (
            int(round(self.crop.x)),
            int(round(self.crop.y)),
            int(round(self.crop.x + self.crop.size)),
            int(round(self.crop.y + self.crop.size)),
        )
        crop = self.source.crop(box)
        size = int(self.output_size.get())
        crop = crop.resize((size, size), Image.Resampling.LANCZOS).convert("RGB")
        bg_mask = self._background_mask(crop) if self.remove_bg.get() else None
        return quantize_to_ink(
            crop,
            bg_mask,
            mode=self.render_mode.get(),
            dither_strength=self.detail_strength.get() / 100.0,
        )

    def _background_mask(self, image: Image.Image) -> set[tuple[int, int]]:
        pixels = image.load()
        w, h = image.size
        border = []
        for x in range(w):
            border.append(pixels[x, 0])
            border.append(pixels[x, h - 1])
        for y in range(h):
            border.append(pixels[0, y])
            border.append(pixels[w - 1, y])

        bg = median_color(border)
        threshold = 18 + int(self.bg_strength.get() * 2.15)
        visited: set[tuple[int, int]] = set()
        queue: deque[tuple[int, int]] = deque()

        for x in range(w):
            queue.append((x, 0))
            queue.append((x, h - 1))
        for y in range(h):
            queue.append((0, y))
            queue.append((w - 1, y))

        while queue:
            x, y = queue.popleft()
            if (x, y) in visited or x < 0 or y < 0 or x >= w or y >= h:
                continue
            if color_distance(pixels[x, y], bg) > threshold:
                continue
            visited.add((x, y))
            queue.append((x + 1, y))
            queue.append((x - 1, y))
            queue.append((x, y + 1))
            queue.append((x, y - 1))
        return visited

    def update_preview(self) -> None:
        ink = self.make_ink_image()
        self.preview_canvas.delete("all")
        if ink is None:
            self.preview_canvas.create_text(160, 160, text="Sin preview", fill="#666666")
            return
        preview = ink.resize((320, 320), Image.Resampling.NEAREST)
        self.preview_photo = ImageTk.PhotoImage(preview)
        self.preview_canvas.create_image(0, 0, anchor="nw", image=self.preview_photo)
        self.preview_canvas.create_rectangle(0, 0, 319, 319, outline="#cfcfcf")

    def export_output(self) -> None:
        ink = self.make_ink_image()
        if ink is None:
            messagebox.showinfo("Sin imagen", "Abre una foto antes de exportar.")
            return

        default_name = "sticker.epd"
        if self.source_path:
            default_name = self.source_path.stem + ".epd"
        path = filedialog.asksaveasfilename(
            title="Guardar imagen e-paper",
            defaultextension=".epd",
            initialfile=default_name,
            filetypes=[
                ("E-paper binario", "*.epd"),
                ("Todos los archivos", "*.*"),
            ],
        )
        if not path:
            return

        out_path = Path(path)
        write_epd(out_path, ink)
        preview_path = out_path.with_name(out_path.stem + "_preview.png")
        ink.save(preview_path)
        self.status.set(f"Exportado: {out_path.name} + {preview_path.name}")
        messagebox.showinfo("Exportado", f"Guardado:\n{out_path}\n{preview_path}")


def median_color(colors: list[tuple[int, int, int]]) -> tuple[int, int, int]:
    rs = sorted(c[0] for c in colors)
    gs = sorted(c[1] for c in colors)
    bs = sorted(c[2] for c in colors)
    mid = len(colors) // 2
    return rs[mid], gs[mid], bs[mid]


def color_distance(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    dr = a[0] - b[0]
    dg = a[1] - b[1]
    db = a[2] - b[2]
    return math.sqrt(dr * dr + dg * dg + db * db)


def is_red_like(r: int, g: int, b: int) -> bool:
    max_c = max(r, g, b)
    min_c = min(r, g, b)
    saturation = max_c - min_c
    if r < 95 or saturation < 18:
        return False
    pink_or_red = r > g + 18 and r > b - 10
    blush = r > 145 and b > g and r > g + 12
    return pink_or_red or blush


def clamp_channel(value: float) -> float:
    return max(0.0, min(255.0, value))


def ink_distance(pixel: tuple[float, float, float], target: tuple[int, int, int]) -> float:
    r, g, b = pixel
    tr, tg, tb = target
    dr = r - tr
    dg = g - tg
    db = b - tb
    return 0.30 * dr * dr + 0.59 * dg * dg + 0.11 * db * db


def nearest_ink_color(pixel: tuple[float, float, float]) -> tuple[int, int, int]:
    r, g, b = pixel
    luma = 0.299 * r + 0.587 * g + 0.114 * b
    redish = is_red_like(int(r), int(g), int(b))
    saturated_light = (max(r, g, b) - min(r, g, b)) > 35 and luma > 135

    best_color = INK_WHITE
    best_score = float("inf")
    for color in (INK_WHITE, INK_BLACK, INK_RED):
        score = ink_distance(pixel, color)

        if color == INK_RED:
            if redish:
                score *= 0.28
            else:
                score += 28000

        if color == INK_BLACK and saturated_light:
            score += 9000

        if color == INK_WHITE and redish:
            score *= 1.15

        if score < best_score:
            best_score = score
            best_color = color
    return best_color


def quantize_clean(image: Image.Image, bg_mask: set[tuple[int, int]] | None = None) -> Image.Image:
    out = Image.new("RGB", image.size, INK_WHITE)
    src = image.load()
    dst = out.load()
    w, h = image.size

    for y in range(h):
        for x in range(w):
            if bg_mask is not None and (x, y) in bg_mask:
                dst[x, y] = INK_WHITE
                continue

            r, g, b = src[x, y]
            luma = 0.299 * r + 0.587 * g + 0.114 * b

            if is_red_like(r, g, b):
                dst[x, y] = INK_RED
            elif luma < 178:
                dst[x, y] = INK_BLACK
            else:
                dst[x, y] = INK_WHITE
    return out


def smoothstep(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return value * value * (3.0 - 2.0 * value)


def quantize_detailed(image: Image.Image, bg_mask: set[tuple[int, int]] | None = None, dither_strength: float = 0.70) -> Image.Image:
    detail = max(0.0, min(1.0, dither_strength))
    if detail <= 0.0:
        return quantize_clean(image, bg_mask)

    mix = smoothstep(detail)
    enhanced = ImageEnhance.Contrast(image).enhance(1.08)
    enhanced = ImageEnhance.Sharpness(enhanced).enhance(1.35)
    clean = quantize_clean(image, bg_mask)
    enhanced = Image.blend(clean, enhanced, mix)

    w, h = enhanced.size
    src = enhanced.load()
    work = [[[float(v) for v in src[x, y]] for x in range(w)] for y in range(h)]
    out = Image.new("RGB", enhanced.size, INK_WHITE)
    dst = out.load()

    def add_error(px: int, py: int, err: tuple[float, float, float], factor: float) -> None:
        if px < 0 or py < 0 or px >= w or py >= h:
            return
        if bg_mask is not None and (px, py) in bg_mask:
            return
        for channel in range(3):
            work[py][px][channel] = clamp_channel(work[py][px][channel] + err[channel] * factor * mix)

    for y in range(h):
        for x in range(w):
            if bg_mask is not None and (x, y) in bg_mask:
                dst[x, y] = INK_WHITE
                continue

            old = tuple(clamp_channel(v) for v in work[y][x])
            new = nearest_ink_color(old)
            dst[x, y] = new
            err = (old[0] - new[0], old[1] - new[1], old[2] - new[2])

            add_error(x + 1, y, err, 7 / 16)
            add_error(x - 1, y + 1, err, 3 / 16)
            add_error(x, y + 1, err, 5 / 16)
            add_error(x + 1, y + 1, err, 1 / 16)
    return out


def quantize_to_ink(
    image: Image.Image,
    bg_mask: set[tuple[int, int]] | None = None,
    mode: str = "Detallado",
    dither_strength: float = 0.70,
) -> Image.Image:
    if mode == "Sticker limpio":
        return quantize_clean(image, bg_mask)
    return quantize_detailed(image, bg_mask, dither_strength)


def pack_plane(image: Image.Image, color: tuple[int, int, int]) -> bytes:
    pixels = image.load()
    w, h = image.size
    data = bytearray()
    current = 0
    bit_count = 0

    for y in range(h):
        for x in range(w):
            current <<= 1
            if pixels[x, y] == color:
                current |= 1
            bit_count += 1
            if bit_count == 8:
                data.append(current)
                current = 0
                bit_count = 0
    if bit_count:
        current <<= 8 - bit_count
        data.append(current)
    return bytes(data)


def write_epd(path: Path, image: Image.Image) -> None:
    if image.width != image.height:
        raise ValueError("La imagen debe ser cuadrada")
    black = pack_plane(image, INK_BLACK)
    red = pack_plane(image, INK_RED)
    header = b"INK3" + struct.pack("<BHH", 1, image.width, image.height)
    path.write_bytes(header + black + red)


if __name__ == "__main__":
    Foto2InkApp().run()
