"""Pruebas sin ventanas; ejecutar desde la raíz con python -B -m unittest ..."""
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import Mock, patch

from PIL import Image

from foto2ink.batch_export import BatchSession, save_batch_image
from foto2ink.foto2ink import CropBox, Foto2InkApp, INK_BLACK, INK_RED, epd_bytes, write_epd


class Value:
    def __init__(self, value):
        self.value = value

    def get(self):
        return self.value

    def set(self, value):
        self.value = value


class BatchExportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.image = Image.new("RGB", (64, 64), "white")
        self.image.putpixel((0, 0), INK_BLACK)
        self.image.putpixel((1, 0), INK_RED)

    def test_many_paths_are_lazy_and_keep_selection_order(self):
        paths = tuple(self.folder / f"{i}.jpg" for i in range(1500))
        batch = BatchSession(paths + (paths[0],), self.folder)
        self.assertEqual(batch.sources, paths)
        self.assertEqual(batch.current, paths[0])
        self.assertEqual(batch.saved, [])

    def test_validates_empty_selection_and_destination(self):
        with self.assertRaises(ValueError):
            BatchSession((), self.folder)
        with self.assertRaises(ValueError):
            BatchSession((Path("photo.jpg"),), self.folder / "missing")

    def test_real_exports_and_queue_completion(self):
        batch = BatchSession((Path("uno.jpg"), Path("dos.jpg"), Path("tres.jpg")), self.folder)
        first = save_batch_image(batch.output_dir, batch.current, self.image, epd_bytes(self.image))
        batch.advance(first)
        batch.advance()  # Omitir es explícito, nunca exporta.
        last = save_batch_image(batch.output_dir, batch.current, self.image, epd_bytes(self.image))
        batch.advance(last)
        self.assertIsNone(batch.current)
        self.assertEqual(len(batch.saved), 2)
        self.assertEqual(batch.skipped, 1)
        self.assertFalse((self.folder / "dos.epd").exists())
        with Image.open(first.preview) as preview:
            self.assertEqual(preview.size, self.image.size)
            self.assertEqual(preview.tobytes(), self.image.tobytes())
        with self.assertRaises(ValueError):
            batch.advance()

    def test_duplicate_stems_and_existing_epd_never_overwrite(self):
        existing = self.folder / "foto.epd"
        existing.write_bytes(b"conservar")
        a = save_batch_image(self.folder, Path("a/foto.jpg"), self.image, epd_bytes(self.image))
        b = save_batch_image(self.folder, Path("b/foto.png"), self.image, epd_bytes(self.image))
        self.assertEqual(existing.read_bytes(), b"conservar")
        self.assertEqual(a.epd.name, "foto_001.epd")
        self.assertEqual(b.epd.name, "foto_002.epd")

    def test_existing_preview_and_source_photo_are_preserved(self):
        original = self.folder / "foto_preview.png"
        original.write_bytes(b"original intocable")
        result = save_batch_image(self.folder, Path("foto.jpg"), self.image, epd_bytes(self.image))
        self.assertEqual(original.read_bytes(), b"original intocable")
        self.assertEqual(result.epd.name, "foto_001.epd")
        self.assertFalse((self.folder / "foto.epd").exists())

    def test_png_failure_cleans_only_new_files_and_queue_does_not_advance(self):
        batch = BatchSession((Path("foto.jpg"),), self.folder)
        existing = self.folder / "old.epd"
        existing.write_bytes(b"old")
        with patch.object(Image.Image, "save", side_effect=OSError("Disco lleno")):
            with self.assertRaises(OSError):
                save_batch_image(self.folder, batch.current, self.image, epd_bytes(self.image))
        self.assertEqual(list(self.folder.iterdir()), [existing])
        self.assertEqual(batch.index, 0)
        self.assertEqual(batch.saved, [])

    def test_preview_creation_failure_does_not_leave_empty_epd(self):
        original_open = Path.open

        def open_file(path, *args, **kwargs):
            if path.suffix == ".png":
                raise PermissionError("Sin permiso")
            return original_open(path, *args, **kwargs)

        with patch.object(Path, "open", open_file):
            with self.assertRaises(PermissionError):
                save_batch_image(self.folder, Path("foto.jpg"), self.image, epd_bytes(self.image))
        self.assertEqual(list(self.folder.iterdir()), [])

    def test_ink3_format_and_single_export_unchanged(self):
        data = epd_bytes(self.image)
        self.assertEqual(data[:4], b"INK3")
        self.assertEqual(struct.unpack("<BHH", data[4:9]), (1, 64, 64))
        self.assertEqual(len(data), 9 + 2 * 64 * 64 // 8)
        self.assertEqual(data[9], 0x80)
        self.assertEqual(data[9 + 64 * 64 // 8], 0x40)
        write_epd(self.folder / "single.epd", self.image)
        self.assertEqual((self.folder / "single.epd").read_bytes(), data)

    def test_non_byte_aligned_planes(self):
        image = Image.new("RGB", (65, 65), INK_RED)
        data = epd_bytes(image)
        plane_size = (65 * 65 + 7) // 8
        self.assertEqual(len(data), 9 + 2 * plane_size)
        self.assertEqual(data[-1], 0x80)
        with self.assertRaises(ValueError):
            epd_bytes(Image.new("RGB", (64, 65)))

    def editor(self):
        app = Foto2InkApp.__new__(Foto2InkApp)  # No Tk() ni ventanas en tests.
        app.source = self.image
        app.crop = CropBox(0, 0, 64)
        app.output_size = Value(64)
        app.remove_bg = Value(False)
        app.render_mode = Value("Sticker limpio")
        app.bg_strength = Value(45)
        app.detail_strength = Value(70)
        app._cached_key = app._cached_ink = None
        app.status = Value("")
        return app

    def test_crop_settings_output_size_and_preview_cache(self):
        app = self.editor()
        first = app.make_ink_image()
        self.assertIs(first, app.make_ink_image())
        app.output_size.set(124)
        second = app.make_ink_image()
        self.assertEqual(second.size, (124, 124))
        self.assertIsNot(first, second)
        app.crop = CropBox(32, 32, 32)
        self.assertIsNot(second, app.make_ink_image())
        app.detail_strength.set(20)
        self.assertNotEqual(app._cached_key[-1], app.detail_strength.get())
        app.make_ink_image()
        self.assertEqual(app._cached_key[-1], 20)

    def test_next_image_gets_own_centered_crop_and_keeps_settings(self):
        path = self.folder / "wide.png"
        Image.new("RGB", (300, 100), "white").save(path)
        app = self.editor()
        app.source_path = None
        app.crop_scale = Value(60)
        app._fit_image_to_canvas = Mock()
        app.redraw = app.update_preview = Mock()
        app._load_image(path)
        self.assertEqual(app.crop, CropBox(120, 20, 60))
        self.assertEqual(app.crop_scale.get(), 60)
        self.assertEqual(app.output_size.get(), 64)
        self.assertEqual(app.render_mode.get(), "Sticker limpio")
        self.assertEqual(app.detail_strength.get(), 70)
        app._load_image(path, keep_crop_size=False)
        self.assertEqual(app.crop.size, 80)

    def test_failed_next_photo_cannot_export_previous_image(self):
        app = self.editor()
        app.batch = BatchSession((self.folder / "missing.jpg",), self.folder)
        app.redraw = app.update_preview = app._sync_batch_controls = Mock()
        app.root = Mock()
        with patch("foto2ink.foto2ink.messagebox.showerror"):
            app._load_batch_current()
        self.assertIsNone(app.source)
        self.assertIsNone(app.source_path)
        self.assertEqual(app.batch.index, 0)

    def test_unsafe_large_image_cannot_leave_previous_photo_exportable(self):
        app = self.editor()
        app.batch = BatchSession((Path("too_big.png"),), self.folder)
        app.redraw = app.update_preview = app._sync_batch_controls = Mock()
        app.root = Mock()
        app._load_image = Mock(side_effect=Image.DecompressionBombError("Imagen demasiado grande"))
        with patch("foto2ink.foto2ink.messagebox.showerror"):
            app._load_batch_current()
        self.assertIsNone(app.source)
        self.assertIsNone(app.source_path)
        self.assertEqual(app.batch.index, 0)

    def test_done_saves_and_advances_once_with_double_click_guard(self):
        app = self.editor()
        app.batch = BatchSession((Path("first.jpg"), Path("second.jpg")), self.folder)
        app.batch_busy = False
        app.root = Mock()
        app._sync_batch_controls = Mock()
        app._load_batch_current = Mock()
        app.batch_done()
        app.batch_done()  # Segundo clic pendiente: no guardar siguiente foto.
        self.assertEqual(app.batch.index, 1)
        self.assertTrue((self.folder / "first.epd").exists())
        self.assertFalse((self.folder / "second.epd").exists())
        self.assertEqual(app.root.after.call_args.args[0], 300)

    def test_done_write_error_keeps_current_photo(self):
        app = self.editor()
        app.batch = BatchSession((Path("foto.jpg"),), self.folder)
        app.batch_busy = False
        app.root = Mock()
        app._sync_batch_controls = Mock()
        app._load_batch_current = Mock()
        with patch("foto2ink.foto2ink.save_batch_image", side_effect=PermissionError("Sin permiso")), patch("foto2ink.foto2ink.messagebox.showerror"):
            app.batch_done()
        self.assertEqual(app.batch.index, 0)
        self.assertEqual(app.batch.saved, [])
        app._load_batch_current.assert_not_called()

    def test_complete_batch_real_loading_rendering_and_export_without_tk(self):
        one = self.folder / "01.png"
        two = self.folder / "02.png"
        Image.new("RGB", (300, 100), INK_RED).save(one)
        Image.new("RGB", (100, 300), INK_BLACK).save(two)
        destination = self.folder / "exportados"
        destination.mkdir()
        app = self.editor()
        app.root = Mock()
        app.batch = BatchSession((one, two), destination)
        app.batch_busy = False
        app.crop_scale = Value(60)
        app.output_size.set(124)
        app._fit_image_to_canvas = Mock()
        app.redraw = Mock()
        app.update_preview = Mock()
        app._sync_batch_controls = Mock()
        app._load_batch_current()
        self.assertEqual(app.crop, CropBox(120, 20, 60))
        app.crop.x = 100
        app.batch_done()
        self.assertEqual(app.source_path, two)
        self.assertEqual(app.crop, CropBox(20, 120, 60))
        app._batch_unlock()
        app.batch_done()
        self.assertIsNone(app.batch.current)
        self.assertEqual(len(app.batch.saved), 2)
        self.assertEqual(app.batch.skipped, 0)
        for result, color in zip(app.batch.saved, (INK_RED, INK_BLACK)):
            self.assertEqual(struct.unpack("<BHH", result.epd.read_bytes()[4:9]), (1, 124, 124))
            with Image.open(result.preview) as preview:
                self.assertEqual(preview.size, (124, 124))
                self.assertEqual(preview.getpixel((60, 60)), color)


if __name__ == "__main__":
    unittest.main()
