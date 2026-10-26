"""UI callbacks with fake variables/windows. Does not create Tk or open a device."""
from pathlib import Path
import queue
import tempfile
import unittest
from unittest.mock import Mock, patch

from .usb_calendar import UsbCalendarApp


class Value:
    def __init__(self, value): self.value = value
    def get(self): return self.value
    def set(self, value): self.value = value


class UiActionTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        for folder in ("firmware", "tools", "assets/phrases"):
            (root / folder).mkdir(parents=True)
        (root / "firmware/platformio.ini").write_text("[env:seeed_xiao_esp32c3]\nbuild_flags = -D INK_WIFI_DISABLED=1\n")
        for path in ("tools/generate_personal_assets.py", "assets/phrases/love_phrases.json", "assets/personal_content.json"):
            (root / path).write_text("{}")
        self.app = UsbCalendarApp.__new__(UsbCalendarApp)
        self.app.root = Mock()
        self.app.project_root, self.app.profile = Value(str(root)), Value("XIAO ESP32-C3")
        self.app.port, self.app.android_catalog = Value("COM4 — USB"), Value(False)
        self.app.updating = self.app.connected = self.app.closing = False
        self.app.busy = self.app.automatic = False
        self.app.worker = Mock()
        self.app.editor = None
        self.app.events = queue.Queue()
        self.app.activity = Value("")
        self.app.last_status = {}
        self.app.stop_auto, self.app.update_controls = Mock(), Mock()

    @patch("usb_calendar.usb_calendar.messagebox")
    @patch("usb_calendar.usb_calendar.FirmwareUpdate")
    def test_confirmed_update_passes_fixed_profile_port_and_controller(self, updater, dialogs):
        dialogs.askyesno.return_value = True
        self.app.start_update()
        self.assertTrue(self.app.updating)
        updater.return_value.start.assert_called_once()
        arguments, keywords = updater.call_args
        self.assertEqual(arguments[1:3], ("seeed_xiao_esp32c3", "COM4"))
        self.assertIs(keywords["controller"], self.app.worker)
        self.assertIsNone(keywords["android_output"])
        self.app.stop_auto.assert_called_once()

    @patch("usb_calendar.usb_calendar.messagebox")
    @patch("usb_calendar.usb_calendar.FirmwareUpdate")
    def test_cancelled_confirmation_has_no_effect(self, updater, dialogs):
        dialogs.askyesno.return_value = False
        self.app.start_update()
        updater.assert_not_called()
        self.app.stop_auto.assert_not_called()
        self.assertFalse(self.app.updating)

    @patch("usb_calendar.usb_calendar.messagebox")
    @patch("usb_calendar.usb_calendar.FirmwareUpdate")
    def test_open_editor_and_demo_block_update(self, updater, dialogs):
        self.app.editor = Mock()
        self.app.start_update()
        dialogs.showinfo.assert_called_once()
        self.app.editor = None
        self.app.port = Value("DEMO — sin dispositivo")
        self.app.start_update()
        dialogs.showerror.assert_called_once()
        updater.assert_not_called()

    @patch("usb_calendar.usb_calendar.messagebox")
    @patch("usb_calendar.usb_calendar.FirmwareUpdate")
    def test_board_profile_mismatch_blocks_update(self, updater, dialogs):
        self.app.connected = True
        self.app.last_status = {"board": "ESP32-C3 Super Mini"}
        self.app.start_update()
        updater.assert_not_called()
        dialogs.showerror.assert_called_once()

    @patch("usb_calendar.usb_calendar.messagebox")
    def test_close_and_serial_actions_are_blocked_during_update(self, dialogs):
        self.app.updating = True
        self.app.close()
        dialogs.showinfo.assert_called_once()
        self.app.root.destroy.assert_not_called()
        self.app.send("date", "2026-10-26")
        self.app.worker.submit.assert_not_called()
        self.app.toggle_connection()
        self.app.worker.disconnect.assert_not_called()

    @patch("usb_calendar.usb_calendar.messagebox")
    def test_clock_sync_requires_explicit_confirmation(self, dialogs):
        self.app.connected = True
        self.app.send = Mock()
        dialogs.askyesno.return_value = False
        self.app.sync_time()
        self.app.send.assert_not_called()
        dialogs.askyesno.return_value = True
        self.app.sync_time()
        self.app.send.assert_called_once_with("sync_time")
        self.assertIn("reales", dialogs.askyesno.call_args.args[1])
        self.assertIn("zona horaria", dialogs.askyesno.call_args.args[1])

    @patch("usb_calendar.usb_calendar.messagebox")
    def test_clock_sync_is_disabled_offline_or_while_busy_auto_closing_or_updating(self, dialogs):
        self.app.send = Mock()
        self.app.sync_time()
        self.app.connected = True
        for flag in ("busy", "automatic", "closing", "updating"):
            setattr(self.app, flag, True)
            self.app.sync_time()
            setattr(self.app, flag, False)
        self.app.send.assert_not_called()
        dialogs.askyesno.assert_not_called()


if __name__ == "__main__":
    unittest.main()
