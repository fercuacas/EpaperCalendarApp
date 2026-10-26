"""Safe update tests: temporary projects and fake processes only. Never opens a COM port."""
import json
from pathlib import Path
import queue
import shutil
import struct
import tempfile
import unittest
from unittest.mock import Mock, patch

from .firmware_update import FirmwareProject, FirmwareUpdate, application_partition


class UpdateTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for folder in ("firmware", "tools", "assets/phrases", "assets/images"):
            (self.root / folder).mkdir(parents=True, exist_ok=True)
        # This fixture generator is intentionally small and deterministic, not user code/assets.
        (self.root / "tools/generate_personal_assets.py").write_text('''import hashlib, json
def load_assets(root):
    phrases = json.loads((root/'assets/phrases/love_phrases.json').read_text(encoding='utf-8'))['phrases']
    assert isinstance(phrases, list)
    for phrase in phrases:
        if not phrase or len(phrase)>160: raise ValueError('Frase invalida')
    load_schedule(root, [])
    return phrases, [], []
def load_schedule(root, images):
    value=json.loads((root/'assets/personal_content.json').read_text(encoding='utf-8'))
    if value.get('invalid'): raise ValueError('Reglas invalidas')
    return value
def assets_fingerprint(phrases, images, schedule):
    return hashlib.sha256(json.dumps([phrases,schedule],sort_keys=True).encode()).hexdigest()[:16]
def export_android(root, output):
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text('catalogue')
''', encoding="utf-8")
        (self.root / "firmware/platformio.ini").write_text("[env:seeed_xiao_esp32c3]\nbuild_flags = -D INK_WIFI_DISABLED=1\n[env:esp32c3_supermini]\n")
        (self.root / "assets/phrases/love_phrases.json").write_text(json.dumps({"phrases": ["Antes"], "retained": "extra"}))
        (self.root / "assets/personal_content.json").write_text(json.dumps({"version": 1, "specialPhrases": [], "specialImages": []}))
        self.project = FirmwareProject(self.root)
        self.events = queue.Queue()
        self.build = self.root / "firmware/.pio/build/seeed_xiao_esp32c3"
        (self.build / "generated").mkdir(parents=True)
        self.table = struct.pack("<HBBII16sI", 0x50aa, 0, 0x10, 0x10000, 0x140000, b"app0", 0)
        (self.build / "partitions.bin").write_bytes(self.table)
        self.calls = []

    def runner(self, arguments, cwd, emit, timeout=900):
        self.calls.append(list(arguments))
        if "run" in arguments:
            version = self.project.catalog()["version"]
            (self.build / "generated/PersonalAssets.generated.h").write_text(f'const char fingerprint[] = "{version}";')
            (self.build / "firmware.bin").write_bytes(b"\xe9" + bytes(30) + version.encode())
        if "read_flash" in arguments:
            Path(arguments[-1]).write_bytes(self.table)

    def execute(self, **kwargs):
        job = FirmwareUpdate(self.project, "seeed_xiao_esp32c3", "COM4", self.events, runner=self.runner, **kwargs)
        with patch("usb_calendar.firmware_update.pio_command", return_value=["fake-pio"]), \
             patch("usb_calendar.firmware_update.esptool_script", return_value=Path("fake-esptool.py")):
            return job.execute()

    def test_editor_saves_utf8_preserves_other_fields_and_validates_before_writing(self):
        _, _, rules, original = self.project.load_editor()
        self.project.save_editor("Nueva frase\n\nCon cariño\n", rules, original)
        data = json.loads(self.project.phrases_path.read_text(encoding="utf-8"))
        self.assertEqual(data["phrases"], ["Nueva frase", "Con cariño"])
        self.assertEqual(data["retained"], "extra")
        _, _, rules, original = self.project.load_editor()
        with self.assertRaises(ValueError): self.project.save_editor("x"*161, rules, original)
        self.assertEqual(self.project.phrases_path.read_bytes(), original[self.project.phrases_path])
        with self.assertRaises(ValueError): self.project.save_editor("Valid", '{"invalid":true}', original)
        self.assertEqual(self.project.phrases_path.read_bytes(), original[self.project.phrases_path])
        self.assertFalse(list(self.project.phrases_path.parent.glob("*.usb-*")))

    def test_editor_does_not_overwrite_changes_from_another_editor(self):
        _, _, rules, original = self.project.load_editor()
        self.project.phrases_path.write_text('{"phrases":["External"]}')
        with self.assertRaisesRegex(ValueError, "otro editor"):
            self.project.save_editor("Mine", rules, original)
        self.assertIn("External", self.project.phrases_path.read_text())

    def test_update_releases_port_parks_loader_builds_checks_table_and_writes_only_app(self):
        controller = Mock()
        controller.is_alive.return_value = False
        output = self.root / "android/catalog.json"
        result = self.execute(controller=controller, android_output=output)
        controller.disconnect.assert_called_once()
        controller.join.assert_called_once()
        self.assertIn("chip_id", self.calls[0])
        self.assertIn("no_reset", self.calls[0])
        self.assertIn("run", self.calls[1])
        self.assertIn("read_flash", self.calls[2])
        self.assertIn("write_flash", self.calls[3])
        self.assertEqual(self.calls[3][-2], "0x10000")
        self.assertIn("hard_reset", self.calls[3])
        for args in self.calls:
            self.assertFalse(any(arg in args for arg in ("erase_flash", "erase_region", "uploadfs", "buildfs")))
        self.assertTrue(output.is_file())
        self.assertEqual(result["phrases"], 1)

    def test_partition_mismatch_never_writes_or_repartitions(self):
        original = self.runner
        def mismatched(args, *rest, **kwargs):
            original(args, *rest, **kwargs)
            if "read_flash" in args: Path(args[-1]).write_bytes(bytes(len(self.table)))
        self.runner = mismatched
        with self.assertRaisesRegex(ValueError, "particiones"):
            self.execute()
        self.assertFalse(any("write_flash" in args for args in self.calls))

    def test_build_failure_stops_before_partition_read_or_flash(self):
        original = self.runner
        def failed(args, *rest, **kwargs):
            original(args, *rest, **kwargs)
            if "run" in args: raise RuntimeError("Compiler error")
        self.runner = failed
        with self.assertRaisesRegex(RuntimeError, "Compiler"):
            self.execute()
        self.assertFalse(any("write_flash" in args or "read_flash" in args for args in self.calls))

    def test_changed_resources_mid_build_abort_and_old_binary_is_rejected(self):
        original = self.runner
        def changed(args, *rest, **kwargs):
            original(args, *rest, **kwargs)
            if "run" in args: self.project.phrases_path.write_text('{"phrases":["Changed"]}')
        self.runner = changed
        with self.assertRaisesRegex(ValueError, "cambiaron"):
            self.execute()
        self.assertFalse(any("write_flash" in args for args in self.calls))
        self.runner = original
        def old_binary(args, *rest, **kwargs):
            original(args, *rest, **kwargs)
            if "run" in args: (self.build / "firmware.bin").write_bytes(b"\xe9" + bytes(100))
        self.runner = old_binary
        with self.assertRaisesRegex(ValueError, "no es válido"):
            self.execute()

    def test_demo_and_radio_enabled_xiao_are_rejected_before_any_command(self):
        job = FirmwareUpdate(self.project, "seeed_xiao_esp32c3", "DEMO", self.events, runner=self.runner)
        with self.assertRaisesRegex(ValueError, "DEMO"): job.execute()
        (self.root / "firmware/platformio.ini").write_text("[env:seeed_xiao_esp32c3]\nbuild_flags = -D INK_WIFI_DISABLED=0\n")
        with self.assertRaisesRegex(ValueError, "INK_WIFI_DISABLED"):
            self.project.validate_profile("seeed_xiao_esp32c3")
        self.assertEqual(self.calls, [])

    def test_invalid_phrase_aborts_before_even_entering_bootloader(self):
        self.project.phrases_path.write_text('{"phrases":[""]}')
        with self.assertRaisesRegex(ValueError, "Frase"):
            self.execute()
        self.assertEqual(self.calls, [])

    def test_busy_controller_prevents_loader_and_flash(self):
        controller = Mock()
        controller.is_alive.return_value = True
        with self.assertRaisesRegex(RuntimeError, "liberado"):
            self.execute(controller=controller)
        self.assertEqual(self.calls, [])

    def test_partition_parser_rejects_unsupported_layouts(self):
        self.assertEqual(application_partition(self.table), (0x10000, 0x140000))
        for table in (b"", bytes(32), struct.pack("<HBBII16sI",0x50aa,0,0x10,0x20000,0x140000,b"app0",0)):
            with self.assertRaises(ValueError): application_partition(table)


if __name__ == "__main__":
    unittest.main()
