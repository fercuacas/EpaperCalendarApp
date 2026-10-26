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
    Entry,
    Frame,
    IntVar,
    Label,
    OptionMenu,
    Scale,
    StringVar,
    Tk,
    Toplevel,
    filedialog,
    messagebox,
)
from tkinter import HORIZONTAL, LEFT, RIGHT, TOP, BOTH, X, Y

from PIL import Image, ImageEnhance, ImageOps, ImageTk

if __package__:
    from .batch_export import BatchSession, save_batch_image
else:
    from batch_export import BatchSession, save_batch_image


DEFAULT_OUTPUT_SIZE = 160
CANVAS_SIZE = 520
PREVIEW_SIZE = 280
IMAGE_TYPES = [("Imágenes", "*.png *.jpg *.jpeg *.bmp *.webp"), ("Todos los archivos", "*.*")]
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
        self.root.minsize(1000, 790)

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
        self.batch: BatchSession | None = None
        self.last_output_dir: Path | None = None
        self.batch_busy = False
        self.preview_job: str | None = None
        self._cached_key: tuple | None = None
        self._cached_ink: Image.Image | None = None

        self.status = StringVar(value="Abre una foto para empezar.")
        self.remove_bg = BooleanVar(value=False)
        self.crop_percent = StringVar(value="80")
        self.render_mode = StringVar(value="Detallado")
        self.output_size = IntVar(value=DEFAULT_OUTPUT_SIZE)
        self.batch_progress = StringVar()
        self.batch_destination = StringVar()

        self._build_ui()
        self._bind_events()

    def _build_ui(self) -> None:
        top = Frame(self.root, padx=12, pady=10)
        top.pack(side=TOP, fill=X)

        actions = Frame(top)
        actions.pack(fill=X, pady=(0, 6))
        Button(actions, text="Abrir foto", command=self.open_image, width=14).pack(side=LEFT)
        self.export_button = Button(actions, text="Exportar", command=self.export_output, width=14)
        self.export_button.pack(side=LEFT, padx=(8, 0))
        Button(actions, text="Procesar varias fotos…", command=self.open_batch, width=23).pack(side=LEFT, padx=(8, 0))
        Checkbutton(top, text="Borrar fondo", variable=self.remove_bg, command=self.request_preview).pack(side=LEFT)

        Label(top, text="Potencia fondo").pack(side=LEFT, padx=(18, 4))
        self.bg_strength = Scale(top, from_=0, to=100, orient=HORIZONTAL, length=170, command=lambda _v: self.request_preview())
        self.bg_strength.set(45)
        self.bg_strength.pack(side=LEFT)

        Label(top, text="Tamano recorte").pack(side=LEFT, padx=(18, 4))
        self.crop_scale = Scale(top, from_=20, to=100, orient=HORIZONTAL, length=170, command=self.on_crop_size)
        self.crop_scale.set(80)
        self.crop_scale.pack(side=LEFT)

        self.batch_bar = Frame(self.root, padx=12, pady=5)
        batch_actions = Frame(self.batch_bar)
        batch_actions.pack(fill=X)
        Label(batch_actions, textvariable=self.batch_progress, anchor="w", wraplength=520, justify=LEFT).pack(side=LEFT, fill=X, expand=True)
        self.done_button = Button(batch_actions, text="Listo → siguiente", command=self.batch_done, bg="#c41820", fg="white", padx=12)
        self.done_button.pack(side=LEFT, padx=6)
        self.skip_button = Button(batch_actions, text="Saltar foto", command=self.batch_skip)
        self.skip_button.pack(side=LEFT, padx=6)
        Button(batch_actions, text="Cerrar lote", command=self.close_batch).pack(side=LEFT, padx=6)
        Label(self.batch_bar, textvariable=self.batch_destination, anchor="w", wraplength=950).pack(fill=X, pady=(3, 0))

        main = Frame(self.root, padx=12, pady=6)
        self.main_frame = main
        main.pack(fill=BOTH, expand=True)

        left_panel = Frame(main)
        left_panel.pack(side=LEFT, fill=BOTH, expand=True)
        Label(left_panel, text="Recorte cuadrado").pack(anchor="w")
        self.canvas = Canvas(left_panel, width=CANVAS_SIZE, height=CANVAS_SIZE, bg="#f2f2f2", highlightthickness=1, highlightbackground="#bbbbbb")
        self.canvas.pack(fill=BOTH, expand=True)

        right_panel = Frame(main, padx=14)
        right_panel.pack(side=RIGHT, fill=Y)
        Label(right_panel, text="Preview e-paper").pack(anchor="w")
        self.preview_canvas = Canvas(right_panel, width=PREVIEW_SIZE, height=PREVIEW_SIZE, bg="#ffffff", highlightthickness=1, highlightbackground="#bbbbbb")
        self.preview_canvas.pack(pady=(6, 12))

        Label(right_panel, text="Salida cuadrada px").pack(anchor="w")
        self.output_scale = Scale(right_panel, from_=64, to=240, orient=HORIZONTAL, length=260, variable=self.output_size, command=lambda _v: self.request_preview())
        self.output_scale.set(DEFAULT_OUTPUT_SIZE)
        self.output_scale.pack(anchor="w", pady=(0, 10))

        Label(right_panel, text="Modo de conversion").pack(anchor="w")
        OptionMenu(right_panel, self.render_mode, "Detallado", "Sticker limpio", command=lambda _v: self.request_preview()).pack(fill=X, pady=(2, 10))

        Label(right_panel, text="Dither / detalle").pack(anchor="w")
        self.detail_strength = Scale(right_panel, from_=0, to=100, orient=HORIZONTAL, length=260, command=lambda _v: self.request_preview())
        self.detail_strength.set(70)
        self.detail_strength.pack(anchor="w", pady=(0, 12))

        help_text = (
            "Controles:\n"
            "- Arrastra el cuadrado para mover el recorte.\n"
            "- Usa la barra de tamano para cambiar el area.\n"
            "- Exportar crea PNG preview + binario .epd.\n"
            "- En lote: Ctrl+Enter guarda y pasa a la siguiente."
        )
        Label(right_panel, text=help_text, justify=LEFT, anchor="w").pack(fill=X)

        bottom = Frame(self.root, padx=12, pady=8)
        bottom.pack(side=TOP, fill=X)
        Label(bottom, textvariable=self.status, anchor="w").pack(side=LEFT, fill=X, expand=True)

    def _bind_events(self) -> None:
        self.canvas.bind("<ButtonPress-1>", self.on_mouse_down)
        self.canvas.bind("<B1-Motion>", self.on_mouse_drag)
        self.canvas.bind("<MouseWheel>", self.on_mouse_wheel)
        self.root.bind("<Control-Return>", self._batch_shortcut)
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def run(self) -> None:
        self.root.mainloop()

    def open_image(self) -> None:
        if self.batch_busy:
            return
        path = filedialog.askopenfilename(
            title="Abrir foto",
            parent=self.root,
            filetypes=IMAGE_TYPES,
        )
        if not path:
            return
        if not self._confirm_leave_batch():
            return
        self._clear_batch()
        try:
            self._load_image(Path(path), keep_crop_size=False)
        except Exception as exc:
            messagebox.showerror("No se pudo abrir", f"No se pudo abrir la imagen:\n{exc}")

    def _load_image(self, path: Path, keep_crop_size: bool = True) -> None:
        with Image.open(path) as original:
            image = ImageOps.exif_transpose(original).convert("RGB")
        self.source_path = path
        self.source = image
        self.drag_start = self.drag_crop_start = None
        self._cached_key = self._cached_ink = None
        self._fit_image_to_canvas()
        percent = float(self.crop_scale.get()) if keep_crop_size else 80
        side = min(image.size) * percent / 100
        self.crop = CropBox((image.width - side) / 2, (image.height - side) / 2, side)
        self.crop_scale.set(percent)
        self.status.set(f"Imagen abierta: {self.source_path.name}")
        self.redraw()
        self.update_preview()

    def _confirm_leave_batch(self) -> bool:
        return self.batch is None or self.batch.current is None or messagebox.askyesno(
            "Cerrar lote", "Quedan fotos sin guardar. ¿Cerrar el lote?\nLas ya guardadas se conservarán.", parent=self.root)

    def _clear_batch(self) -> None:
        self.batch = None
        self.batch_bar.pack_forget()
        self.export_button.config(state="normal")

    def open_batch(self) -> None:
        if self.batch_busy:
            return
        paths = filedialog.askopenfilenames(title="Seleccionar fotos del lote", parent=self.root, filetypes=IMAGE_TYPES)
        if not paths:
            return
        dialog = Toplevel(self.root)
        dialog.title("Preparar lote")
        dialog.transient(self.root)
        dialog.resizable(False, False)
        folder = StringVar(value=str(self.last_output_dir or ""))
        size = StringVar(value=str(self.output_size.get()))
        body = Frame(dialog, padx=18, pady=16)
        body.pack(fill=BOTH, expand=True)
        Label(body, text=f"{len(paths)} fotos seleccionadas · se revisarán una a una", anchor="w").pack(fill=X)
        Label(body, text="Carpeta donde guardar los .epd y previews:", anchor="w").pack(fill=X, pady=(14, 3))
        folder_row = Frame(body)
        folder_row.pack(fill=X)
        Entry(folder_row, textvariable=folder, width=64).pack(side=LEFT, fill=X, expand=True)

        def browse() -> None:
            chosen = filedialog.askdirectory(title="Carpeta de salida del lote", parent=dialog, initialdir=folder.get() or None)
            if chosen:
                folder.set(chosen)

        Button(folder_row, text="Elegir…", command=browse).pack(side=LEFT, padx=(8, 0))
        Label(body, text="Tamaño inicial de salida cuadrada (64–240 px):", anchor="w").pack(fill=X, pady=(14, 3))
        size_entry = Entry(body, textvariable=size, width=10)
        size_entry.pack(anchor="w")
        Label(body, text="Podrás ajustar recorte, fondo, modo, detalle y tamaño en cada foto.\nLos ajustes se conservan para la siguiente. No se sobrescriben archivos.", justify=LEFT).pack(anchor="w", pady=12)

        def start() -> None:
            try:
                pixels = int(size.get())
                if not 64 <= pixels <= 240:
                    raise ValueError("El tamaño debe estar entre 64 y 240 píxeles.")
                if not folder.get().strip():
                    raise ValueError("Elige primero la carpeta de salida.")
                batch = BatchSession(tuple(Path(p) for p in paths), Path(folder.get()).expanduser())
            except ValueError as exc:
                messagebox.showerror("Revisa el lote", str(exc), parent=dialog)
                return
            if not self._confirm_leave_batch():
                return
            self.batch = batch
            self.last_output_dir = batch.output_dir
            self.output_size.set(pixels)
            dialog.destroy()
            self.batch_bar.pack(before=self.main_frame, fill=X)
            self._load_batch_current()

        buttons = Frame(body)
        buttons.pack(fill=X)
        Button(buttons, text="Cancelar", command=dialog.destroy).pack(side=RIGHT)
        Button(buttons, text="Empezar lote", command=start, width=18).pack(side=RIGHT, padx=8)
        dialog.bind("<Return>", lambda _event: start())
        dialog.bind("<Escape>", lambda _event: dialog.destroy())
        dialog.update_idletasks()
        x = max(0, self.root.winfo_rootx() + (self.root.winfo_width() - dialog.winfo_reqwidth()) // 2)
        y = max(0, self.root.winfo_rooty() + (self.root.winfo_height() - dialog.winfo_reqheight()) // 2)
        dialog.geometry(f"+{x}+{y}")
        dialog.grab_set()
        size_entry.focus_set()

    def _sync_batch_controls(self) -> None:
        if self.batch is None:
            return
        batch = self.batch
        total = len(batch.sources)
        if batch.current is None:
            self.batch_progress.set(f"Lote terminado · {len(batch.saved)} guardadas · {batch.skipped} omitidas")
        else:
            self.batch_progress.set(f"Foto {batch.index + 1}/{total} · {batch.current.name} · {len(batch.saved)} guardadas · {batch.skipped} omitidas")
        self.batch_destination.set(f"Destino: {batch.output_dir}")
        self.done_button.config(state="normal" if batch.current and self.source is not None and not self.batch_busy else "disabled")
        self.skip_button.config(state="normal" if batch.current and not self.batch_busy else "disabled")
        self.export_button.config(state="disabled" if batch.current else "normal")

    def _load_batch_current(self) -> None:
        if self.batch is None:
            return
        path = self.batch.current
        if path is None:
            self.status.set(f"Lote terminado. Archivos guardados en {self.batch.output_dir}")
        else:
            try:
                self._load_image(path)
            except Exception as exc:
                # Nunca dejar exportable la foto anterior si la siguiente falla.
                self.source = self.source_path = self.display_photo = None
                self._cached_key = self._cached_ink = None
                self.redraw()
                self.update_preview()
                self.status.set(f"No se pudo abrir {path.name}. Puedes saltarla.")
                messagebox.showerror("No se pudo abrir", f"{path.name}\n{exc}\nPulsa «Saltar foto» para continuar.", parent=self.root)
        self._sync_batch_controls()

    def batch_done(self) -> None:
        if self.batch_busy or self.batch is None or self.batch.current is None or self.source is None:
            return
        self.batch_busy = True
        self._sync_batch_controls()
        self.root.update_idletasks()
        try:
            ink = self.make_ink_image()
            assert ink is not None
            result = save_batch_image(self.batch.output_dir, self.batch.current, ink, epd_bytes(ink))
            self.batch.advance(result)
            self._load_batch_current()
            if self.batch.current is None:
                self.status.set(f"Lote terminado: {len(self.batch.saved)} guardadas en {self.batch.output_dir}")
            elif self.source is not None:
                self.status.set(f"Guardado: {result.epd.name} + {result.preview.name}")
        except (OSError, ValueError) as exc:
            messagebox.showerror("No se pudo guardar", f"{exc}\nLa foto sigue pendiente. Puedes reintentar o saltarla.", parent=self.root)
        finally:
            # Consumir dobles clics pendientes antes de habilitar la foto nueva.
            self.root.after(300, self._batch_unlock)

    def _batch_unlock(self) -> None:
        self.batch_busy = False
        self._sync_batch_controls()

    def batch_skip(self) -> None:
        if self.batch_busy or self.batch is None or self.batch.current is None:
            return
        self.batch.advance()
        self._load_batch_current()

    def close_batch(self) -> None:
        if not self.batch_busy and self._confirm_leave_batch():
            self._clear_batch()
            self.status.set("Lote cerrado. Las fotos guardadas se conservan.")

    def _batch_shortcut(self, _event) -> str:
        if self.root.grab_current() is None:
            self.batch_done()
        return "break"

    def on_close(self) -> None:
        if not self.batch_busy and self._confirm_leave_batch():
            self.root.destroy()

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
        self.request_preview()

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
        self.request_preview()

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
        key = (id(self.source), box, int(self.output_size.get()), self.remove_bg.get(),
               self.render_mode.get(), self.bg_strength.get(), self.detail_strength.get())
        if key == self._cached_key:
            return self._cached_ink
        crop = self.source.crop(box)
        size = int(self.output_size.get())
        crop = crop.resize((size, size), Image.Resampling.LANCZOS).convert("RGB")
        bg_mask = self._background_mask(crop) if self.remove_bg.get() else None
        ink = quantize_to_ink(
            crop,
            bg_mask,
            mode=self.render_mode.get(),
            dither_strength=self.detail_strength.get() / 100.0,
        )
        self._cached_key, self._cached_ink = key, ink
        return ink

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

    def request_preview(self) -> None:
        if self.preview_job is not None:
            self.root.after_cancel(self.preview_job)
        self.preview_job = self.root.after(100, self.update_preview)

    def update_preview(self) -> None:
        if self.preview_job is not None:
            self.root.after_cancel(self.preview_job)
            self.preview_job = None
        ink = self.make_ink_image()
        self.preview_canvas.delete("all")
        if ink is None:
            self.preview_canvas.create_text(PREVIEW_SIZE // 2, PREVIEW_SIZE // 2, text="Sin preview", fill="#666666")
            return
        preview = ink.resize((PREVIEW_SIZE, PREVIEW_SIZE), Image.Resampling.NEAREST)
        self.preview_photo = ImageTk.PhotoImage(preview)
        self.preview_canvas.create_image(0, 0, anchor="nw", image=self.preview_photo)
        self.preview_canvas.create_rectangle(0, 0, PREVIEW_SIZE - 1, PREVIEW_SIZE - 1, outline="#cfcfcf")

    def export_output(self) -> None:
        if self.batch_busy or (self.batch is not None and self.batch.current is not None):
            return
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
        preview_path = out_path.with_name(out_path.stem + "_preview.png")
        try:
            write_epd(out_path, ink)
            ink.save(preview_path)
        except (OSError, ValueError) as exc:
            messagebox.showerror("No se pudo exportar", str(exc), parent=self.root)
            return
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


def epd_bytes(image: Image.Image) -> bytes:
    if image.width != image.height:
        raise ValueError("La imagen debe ser cuadrada")
    black = pack_plane(image, INK_BLACK)
    red = pack_plane(image, INK_RED)
    header = b"INK3" + struct.pack("<BHH", 1, image.width, image.height)
    return header + black + red


def write_epd(path: Path, image: Image.Image) -> None:
    path.write_bytes(epd_bytes(image))


if __name__ == "__main__":
    Foto2InkApp().run()
