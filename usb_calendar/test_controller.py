"""Headless tests: fake serial transport, no Tk, hardware, network or firmware writes."""
from datetime import date, datetime, timezone
import json
import queue
import threading
import time
import unittest

from .controller import (AutoAdvance, ControllerWorker, DemoTransport, LineDecoder,
                         ConnectionCancelled, ProtocolError, SerialSession, command_bytes, display_pending, validate_action)


class RecordedDemo(DemoTransport):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.writes = []
    def write(self, data):
        self.writes.append(data)
        return super().write(data)


def wait_for(predicate, timeout=5):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate(): return
        time.sleep(0.01)
    raise AssertionError("Timed out waiting for test state")


class ControllerTests(unittest.TestCase):
    def test_startup_retries_only_read_only_probe_and_idempotent_hold(self):
        class DropFirst(RecordedDemo):
            def __init__(self):
                super().__init__(refresh_seconds=0)
                self.dropped = set()
            def write(self, data):
                result = super().write(data)
                key = "probe" if data == b"\nstatus\n" else "hold" if data.endswith(b" hold\n") else "other"
                if key in {"probe", "hold"} and key not in self.dropped:
                    self.dropped.add(key)
                    self.output.clear()
                return result
        demo = DropFirst()
        session = SerialSession(demo)
        self.assertEqual(session.handshake(timeout=4)["usbProtocolVersion"], 1)
        session.request("hold", timeout=4)
        self.assertEqual(demo.writes, [b"\nstatus\n", b"\nstatus\n", b"usb 1 hold\n", b"usb 1 hold\n"])

    def test_disconnect_cancels_silent_startup_promptly_and_closes_port(self):
        class Silent(RecordedDemo):
            def write(self, data):
                self.writes.append(data)
                return len(data)
        demo, events = Silent(), queue.Queue()
        worker = ControllerWorker("FAKE", events, factory=lambda port: demo)
        worker.start()
        wait_for(lambda: bool(demo.writes))
        worker.disconnect()
        worker.join(1)
        self.assertFalse(worker.is_alive())
        self.assertTrue(demo.closed)
        self.assertFalse(any(kind == "error" for kind, value in list(events.queue)))
        self.assertEqual(demo.writes, [b"\nstatus\n"])

    def test_cancellation_before_probe_sends_no_command(self):
        demo = RecordedDemo()
        with self.assertRaises(ConnectionCancelled):
            SerialSession(demo).handshake(cancelled=lambda: True)
        self.assertEqual(demo.writes, [])

    def test_initial_display_wait_clears_busy_before_connected(self):
        demo = RecordedDemo(refresh_seconds=0.15)
        demo.status["refreshPending"] = True
        demo.finish = time.monotonic() + .15
        events = queue.Queue()
        worker = ControllerWorker("FAKE", events, factory=lambda port: demo)
        worker.start()
        self.addCleanup(lambda: self.shutdown(worker))
        wait_for(lambda: worker.connected)
        messages = list(events.queue)
        connected = next(i for i, (kind,value) in enumerate(messages) if kind == "connected")
        busy = [value for kind,value in messages[:connected] if kind == "busy"]
        self.assertTrue(busy)
        self.assertFalse(busy[-1])
        stages = [value for kind,value in messages if kind == "connection_stage"]
        self.assertEqual(len(stages), 3)

    def test_missing_startup_response_has_actionable_timeout_without_mutations(self):
        class Silent(RecordedDemo):
            def write(self, data):
                self.writes.append(data)
                return len(data)
        demo = Silent()
        with self.assertRaisesRegex(TimeoutError, "puerto aparece"):
            SerialSession(demo).handshake(timeout=.03)
        self.assertEqual(demo.writes, [b"\nstatus\n"])

    def test_explicit_clock_sync_confirms_epoch_and_exits_preview_without_wifi(self):
        demo = RecordedDemo(refresh_seconds=0)
        demo.status.update(clockNeedsSync=True, today="2026-01-21")
        session = SerialSession(demo)
        session.request("date", "2028-02-29")
        epoch = int(datetime(2026, 10, 26, 21, 30, tzinfo=timezone.utc).timestamp())
        status = session.synchronize_time(wall_clock=lambda: epoch)
        self.assertEqual(status["utcEpoch"], epoch)
        self.assertEqual(status["today"], datetime.fromtimestamp(epoch).date().isoformat())
        self.assertEqual(status["displayDate"], status["today"])
        self.assertFalse(status["usbPreview"])
        self.assertFalse(status["clockNeedsSync"])
        self.assertEqual(status["clockSource"], "usb")
        self.assertEqual(demo.writes[-1], f"time {epoch}\nusb 3 exit\n".encode())
        self.assertNotIn(b"wifi", b"".join(demo.writes))

    def test_clock_sync_requires_calendar_and_valid_pc_clock_without_writing_time(self):
        for value in (float("nan"), float("inf"), 0, 4102444800, "1\nwifi", True):
            demo = RecordedDemo(refresh_seconds=0)
            with self.subTest(value=value), self.assertRaises(ValueError):
                SerialSession(demo).synchronize_time(wall_clock=lambda: value)
            self.assertFalse(any(b"time " in write for write in demo.writes))
        demo = RecordedDemo(refresh_seconds=0)
        demo.status["hasCalendar"] = False
        with self.assertRaisesRegex(ProtocolError, "calendario válido"):
            SerialSession(demo).synchronize_time()
        self.assertEqual(demo.writes, [b"usb 1 status\n"])

    def test_clock_sync_rejection_is_not_reported_as_success_or_retried(self):
        class RejectClock(RecordedDemo):
            def write(self, data):
                if data.startswith(b"time "):
                    self.writes.append(data)
                    data = data.split(b"\n", 1)[1]
                return super().write(data)
        demo = RejectClock(refresh_seconds=0)
        with self.assertRaisesRegex(ProtocolError, "confirmar"):
            SerialSession(demo).synchronize_time()
        self.assertEqual(sum(b"time " in write for write in demo.writes), 1)

    def test_clock_sync_timeout_never_retries_and_ignores_stale_ack(self):
        class DropClockAck(RecordedDemo):
            def write(self, data):
                result = super().write(data)
                if data.startswith(b"time "):
                    self.output.clear()
                    self.output.extend(json.dumps(dict(usb=1, id=999, ok=True, status=self.status)).encode() + b"\n")
                return result
        demo = DropClockAck(refresh_seconds=0)
        with self.assertRaises(TimeoutError):
            SerialSession(demo).synchronize_time(timeout=0.05)
        self.assertEqual(sum(b"time " in write for write in demo.writes), 1)

    def test_clock_sync_validation_does_not_allow_free_console_arguments(self):
        validate_action("sync_time")
        for argument in ("1790000000", "\nwifi", "2026-10-26"):
            with self.assertRaises(ValueError): validate_action("sync_time", argument)
        with self.assertRaises(ValueError): command_bytes(1, "sync_time")

    def test_command_allowlist_cannot_enable_wifi_flash_reset_or_change_clock(self):
        for verb in ("wifi", "time", "sleep", "reset", "flash", "erase", "awake", "auto", "hold\nwifi"):
            with self.subTest(verb=verb), self.assertRaises(ValueError): command_bytes(1, verb)
        self.assertEqual(command_bytes(7, "date", "2028-02-29"), b"usb 7 date 2028-02-29\n")
        for day in ("2027-02-29", "1899-12-31", "2026-1-01", "2026-01-01\nwifi", "26/10/2026"):
            with self.subTest(day=day), self.assertRaises(ValueError): command_bytes(1, "date", day)
        for value in ("0", "367", "-367", "1\nwifi"):
            with self.assertRaises(ValueError): command_bytes(1, "step", value)
        for request_id in (0, -1, 1000000):
            with self.assertRaises(ValueError): command_bytes(request_id, "status")

    def test_decoder_preserves_fragmented_lines_and_recovers_from_oversize(self):
        decoder = LineDecoder(8)
        self.assertEqual(decoder.feed(b"abc"), [])
        self.assertEqual(decoder.feed(b"d\r\nx\ny"), ["abcd", "x"])
        self.assertEqual(decoder.feed(b"z\n"), ["yz"])
        self.assertEqual(decoder.feed(b"X" * 10000), [])
        self.assertLessEqual(len(decoder.buffer), 8)
        self.assertEqual(decoder.feed(b"\nok\n"), ["ok"])
        self.assertEqual(decoder.feed(b"\xff\n"), ["\ufffd"])

    def test_handshake_is_read_only_and_old_firmware_gives_clear_error(self):
        demo = RecordedDemo(refresh_seconds=0)
        session = SerialSession(demo)
        self.assertEqual(session.handshake()["usbProtocolVersion"], 1)
        self.assertEqual(demo.writes, [b"\nstatus\n"])
        demo.status.pop("usbProtocolVersion")
        with self.assertRaisesRegex(ProtocolError, "firmware"):
            session.handshake()
        self.assertFalse(any(b"hold" in write for write in demo.writes))

    def test_requests_match_ids_ignore_logs_and_old_replies(self):
        demo = RecordedDemo(refresh_seconds=0)
        demo.output.extend(b"Display refresh finished\n{broken}\n" +
                           json.dumps(dict(usb=1, id=999, ok=True, status={})).encode() + b"\n")
        messages = []
        session = SerialSession(demo, messages.append)
        status = session.request("date", "2026-12-25")
        self.assertEqual(status["displayDate"], "2026-12-25")
        self.assertTrue(status["usbPreview"])
        self.assertGreaterEqual(len(messages), 3)
        session.request_id = 999999
        session.request("status")
        self.assertTrue(demo.writes[-1].startswith(b"usb 1 status"))

    def test_timeout_never_retries_a_mutating_command(self):
        class Silent:
            in_waiting = 0
            writes = []
            def write(self, data): self.writes.append(data)
            def read(self, size): return b""
        now = [0]
        def clock():
            now[0] += 0.1; return now[0]
        transport = Silent()
        with self.assertRaises(TimeoutError): SerialSession(transport, clock=clock).request("step", "1", timeout=1)
        self.assertEqual(len(transport.writes), 1)

    def test_rejected_and_malformed_responses_are_not_success(self):
        class Response:
            in_waiting = 100
            def __init__(self, response): self.response = response
            def write(self, data): pass
            def read(self, size):
                response, self.response = self.response, b""; return response
        for response in (dict(usb=1,id=1,ok=False,error="invalid_date"), dict(usb=1,id=1,ok=True)):
            with self.assertRaises(ProtocolError):
                SerialSession(Response(json.dumps(response).encode() + b"\n")).request("status")

    def test_scheduler_waits_from_completion_without_catching_up(self):
        auto = AutoAdvance()
        auto.start(10, 5, -1)
        self.assertFalse(auto.due(14.99))
        self.assertTrue(auto.due(15))
        auto.completed(100)
        self.assertFalse(auto.due(104.99))
        self.assertTrue(auto.due(105))
        auto.stop()
        self.assertFalse(auto.due(100000))
        for interval in (0, -1, 86401, float("nan"), float("inf")):
            with self.assertRaises(ValueError): auto.start(0, interval)

    def worker(self, refresh_seconds=0.1):
        demo, events = RecordedDemo(refresh_seconds=refresh_seconds), queue.Queue()
        worker = ControllerWorker("FAKE", events, factory=lambda port: demo)
        worker.start()
        self.addCleanup(lambda: self.shutdown(worker))
        wait_for(lambda: worker.connected)
        return worker, demo, events

    @staticmethod
    def shutdown(worker):
        worker.disconnect(); worker.join(5)
        if worker.is_alive(): raise AssertionError("Worker did not stop")

    def test_worker_serializes_manual_commands_and_disconnect_restores_normal(self):
        worker, demo, _ = self.worker(0.2)
        real_today = demo.status["today"]
        self.assertTrue(worker.submit("date", "2028-02-29"))
        self.assertFalse(worker.submit("step", "1"))
        wait_for(lambda: not worker.action_pending)
        self.assertEqual(worker.status["displayDate"], "2028-02-29")
        self.assertEqual(worker.status["today"], real_today)
        self.assertFalse(display_pending(worker.status))
        self.shutdown(worker)
        self.assertTrue(demo.closed)
        self.assertFalse(demo.status["usbPreview"])
        self.assertEqual(demo.status["displayDate"], real_today)
        writes = b"".join(demo.writes)
        self.assertNotIn(b"wifi", writes)
        self.assertNotIn(b"time ", writes)

    def test_automatic_stop_during_refresh_does_not_schedule_another_day(self):
        worker, demo, _ = self.worker(0.3)
        self.assertTrue(worker.start_auto(1, 1))
        self.assertFalse(worker.submit("date", "2026-01-01"))
        wait_for(lambda: any(b" step " in data for data in demo.writes))
        worker.stop_auto()
        time.sleep(1.6)
        self.assertEqual(sum(b" step " in data for data in demo.writes), 1)

    def test_worker_sync_samples_clock_when_executed_and_waits_for_refresh(self):
        worker, demo, events = self.worker(0.2)
        epoch = 1793050200
        worker.wall_clock = lambda: epoch
        self.assertTrue(worker.submit("date", "2028-02-29"))
        wait_for(lambda: not worker.action_pending)
        self.assertTrue(worker.submit("sync_time"))
        self.assertFalse(worker.submit("step", "1"))
        wait_for(lambda: not worker.action_pending)
        self.assertEqual(worker.status["utcEpoch"], epoch)
        self.assertFalse(worker.status["usbPreview"])
        self.assertFalse(display_pending(worker.status))
        messages = list(events.queue)
        synced = [value for kind, value in messages if kind == "time_synced"]
        self.assertEqual(len(synced), 1)
        self.assertFalse(display_pending(synced[0]))
        self.assertTrue(demo.writes[-1].endswith(b"hold\n"))
        self.assertTrue(worker.start_auto(10))
        self.assertFalse(worker.submit("sync_time"))
        worker.stop_auto()

    def test_port_errors_are_reported_without_attempting_commands(self):
        events = queue.Queue()
        def failed(port): raise PermissionError("Puerto ocupado")
        worker = ControllerWorker("FAKE", events, factory=failed)
        worker.start(); worker.join(2)
        messages = list(events.queue)
        self.assertTrue(any(kind == "error" and "ocupado" in value for kind, value in messages))
        self.assertIn(("disconnected", None), messages)

    def test_demo_uses_no_hardware_and_preserves_actual_date(self):
        demo = DemoTransport(refresh_seconds=0)
        session = SerialSession(demo)
        real_date = session.handshake()["today"]
        session.request("view", "MONTH")
        result = session.request("date", "2028-02-29")
        self.assertEqual(result["today"], real_date)
        self.assertEqual(result["displayView"], "MONTH")
        self.assertTrue(result["usbPreview"])
        self.assertEqual(result["wifiDriverMode"], 0)


if __name__ == "__main__":
    unittest.main()
