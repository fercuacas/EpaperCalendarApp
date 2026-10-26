"""Cola ligera y exportación sin sobrescritura, independientes de Tkinter."""
from __future__ import annotations

from dataclasses import dataclass, field
from itertools import count
from pathlib import Path

from PIL import Image


@dataclass(frozen=True)
class ExportPaths:
    epd: Path
    preview: Path


@dataclass
class BatchSession:
    sources: tuple[Path, ...]
    output_dir: Path
    index: int = 0
    saved: list[ExportPaths] = field(default_factory=list)
    skipped: int = 0

    def __post_init__(self) -> None:
        # Solo rutas: no cargar cientos de imágenes simultáneamente.
        self.sources = tuple(dict.fromkeys(Path(path) for path in self.sources))
        self.output_dir = Path(self.output_dir)
        if not self.sources:
            raise ValueError("Selecciona al menos una foto.")
        if not self.output_dir.is_dir():
            raise ValueError("Selecciona una carpeta de salida existente.")

    @property
    def current(self) -> Path | None:
        return self.sources[self.index] if self.index < len(self.sources) else None

    def advance(self, result: ExportPaths | None = None) -> None:
        if self.current is None:
            raise ValueError("El lote ya ha terminado.")
        if result is None:
            self.skipped += 1
        else:
            self.saved.append(result)
        self.index += 1


def save_batch_image(output_dir: Path, source: Path, image: Image.Image, epd: bytes) -> ExportPaths:
    """Crea ambos archivos con exclusividad; limpia solo los nuevos si falla.

    El llamador avanza la cola únicamente después de obtener un resultado.
    Comprobar los dos nombres también protege fotos originales en la salida.
    """
    output_dir = Path(output_dir)
    stem = Path(source).stem
    for number in count():
        name = stem if number == 0 else f"{stem}_{number:03d}"
        paths = ExportPaths(output_dir / f"{name}.epd", output_dir / f"{name}_preview.png")
        if paths.epd.exists() or paths.preview.exists():
            continue
        created: list[Path] = []
        try:
            with paths.epd.open("xb") as binary:
                created.append(paths.epd)
                with paths.preview.open("xb") as preview:
                    created.append(paths.preview)
                    binary.write(epd)
                    image.save(preview, format="PNG")
            return paths
        except FileExistsError:
            for path in created:
                path.unlink()
            continue
        except BaseException:
            for path in created:
                path.unlink()
            raise
