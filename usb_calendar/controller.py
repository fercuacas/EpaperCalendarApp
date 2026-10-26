"""Serial control without network/firmware writes; explicit PC clock synchronization."""
from datetime import date, datetime, timedelta
import json
import math
import queue
import threading
import time

VIEWS = ("DAY", "WEEK", "MONTH", "DAILY_CUSTOM")


class ProtocolError(Exception):
    pass


class ConnectionCancelled(Exception):
    """User cancelled initial negotiation; not a hardware failure."""


def command_bytes(request_id, verb, argument=""):
    if not 1 <= request_id <= 999999 or verb not in {"status", "hold", "date", "step", "view", "exit", "refresh"}:
        raise ValueError("Comando USB no permitido")
    argument = str(argument)
    if verb == "date":
        parsed = date.fromisoformat(argument)
        if parsed.isoformat() != argument or parsed.year < 1900:
            raise ValueError("Fecha: usa YYYY-MM-DD, entre 1900 y 9999")
    elif verb == "step":
        if not argument.lstrip("+-").isdigit() or not -366 <= int(argument) <= 366 or int(argument) == 0:
            raise ValueError("Salto: entre -366 y 366, sin cero")
    elif verb == "view":
        if argument not in VIEWS:
            raise ValueError("Vista desconocida")
    elif argument:
        raise ValueError("Este comando no admite argumentos")
    return (f"usb {request_id} {verb}" + (f" {argument}" if argument else "") + "\n").encode("ascii")


def validate_action(verb, argument=""):
    # Clock synchronization is a dedicated operation, not an arbitrary console command.
    if verb == "sync_time":
        if argument:
            raise ValueError("Sincronizar fecha usa exclusivamente el reloj del ordenador")
    else:
        command_bytes(1, verb, argument)


class LineDecoder:
    def __init__(self, limit=8192):
        self.limit = limit
        self.buffer = bytearray()
        self.discarding = False

    def feed(self, chunk):
        lines = []
        for value in chunk:
            if value == 10:
                if not self.discarding:
                    lines.append(self.buffer.decode("utf-8", errors="replace").rstrip("\r"))
                self.buffer.clear(); self.discarding = False
            elif not self.discarding:
                if len(self.buffer) >= self.limit:
                    self.buffer.clear(); self.discarding = True
                else:
                    self.buffer.append(value)
        return lines


class SerialSession:
    def __init__(self, transport, log=lambda text: None, clock=time.monotonic):
        self.transport, self.log, self.clock = transport, log, clock
        self.decoder = LineDecoder()
        self.lines = []
        self.request_id = 0

    def _receive(self, predicate, timeout, cancelled=None):
        deadline = self.clock() + timeout
        while self.clock() < deadline:
            if cancelled and cancelled():
                raise ConnectionCancelled()
            while self.lines:
                line = self.lines.pop(0)
                if not line:
                    continue
                try:
                    value = json.loads(line)
                except (ValueError, TypeError):
                    self.log(line[:1000]); continue
                if isinstance(value, dict) and predicate(value):
                    return value
                self.log("Respuesta USB ajena/antigua ignorada")
            chunk = self.transport.read(min(max(getattr(self.transport, "in_waiting", 1), 1), 1024))
            if chunk:
                self.lines.extend(self.decoder.feed(chunk))
        raise TimeoutError("El calendario no respondió. Puede estar refrescando, dormido o desconectado. No se reenvía la orden para evitar saltar dos días.")

    def handshake(self, timeout=45, cancelled=None):
        # Native USB can enumerate before the first exchange is ready. Only the
        # read-only probe is retried; never replay date/step/time/refresh orders.
        deadline = self.clock() + timeout
        while True:
            if cancelled and cancelled():
                raise ConnectionCancelled()
            remaining = deadline - self.clock()
            if remaining <= 0:
                raise TimeoutError("El puerto aparece, pero el calendario no responde. Pulsa brevemente el central y vuelve a conectar; cierra otros monitores USB.")
            self.transport.write(b"\nstatus\n")
            try:
                status = self._receive(lambda value: "wifiMode" in value and "usb" not in value,
                                       min(1, remaining), cancelled)
                break
            except TimeoutError:
                continue
        if status.get("usbProtocolVersion") != 1:
            raise ProtocolError("Este firmware aún no permite control USB. Carga la actualización de Ink_calendar32; no es necesario activar Wi-Fi.")
        return status

    def request(self, verb, argument="", timeout=45, cancelled=None):
        return self._request(verb, argument, timeout, cancelled=cancelled)

    def _request(self, verb, argument, timeout, prefix=b"", cancelled=None):
        self.request_id = self.request_id % 999999 + 1
        request_id = self.request_id
        wire = prefix + command_bytes(request_id, verb, argument)
        safe_retry = not prefix and verb in {"status", "hold"}
        deadline = self.clock() + timeout
        while True:
            if cancelled and cancelled():
                raise ConnectionCancelled()
            self.transport.write(wire)
            remaining = max(0, deadline - self.clock())
            try:
                response = self._receive(lambda value: value.get("usb") == 1 and value.get("id") == request_id,
                                         min(1, remaining) if safe_retry else remaining, cancelled)
                break
            except TimeoutError:
                if not safe_retry or self.clock() >= deadline:
                    raise
        if response.get("ok") is not True:
            raise ProtocolError("Orden rechazada: " + str(response.get("error", "error desconocido")))
        status = response.get("status")
        if not isinstance(status, dict):
            raise ProtocolError("Respuesta de estado USB incompleta")
        return status

    def synchronize_time(self, wall_clock=time.time, timeout=45):
        """Use the existing firmware console; a correlated reply must confirm the RTC.

        Batch time + exit to discard the USB preview before the main loop redraws.
        Sample UTC at execution, not when queued. Never retry an uncertain write.
        """
        before = self.request("status", timeout=timeout)
        if before.get("hasCalendar") is not True:
            raise ProtocolError("Primero sincroniza un calendario válido; el firmware no ajusta el reloj sin datos")
        value = wall_clock()
        if not isinstance(value, (int, float)) or isinstance(value, bool) or not math.isfinite(value) or not 1577836800 <= value < 4102444800:
            raise ValueError("Revisa el reloj del ordenador: debe estar entre 2020 y 2099")
        epoch = int(value)
        started = self.clock()
        status = self._request("exit", "", timeout, prefix=f"time {epoch}\n".encode("ascii"))
        actual = status.get("utcEpoch")
        elapsed = max(0, self.clock() - started)
        if (type(actual) is not int or not epoch - 2 <= actual <= epoch + math.ceil(elapsed) + 2
                or status.get("clockNeedsSync") is not False or status.get("clockSource") != "usb"
                or status.get("usbPreview") is not False):
            raise ProtocolError("No se pudo confirmar la fecha del reloj. No se reenvía; revisa el estado y el firmware")
        return status


class AutoAdvance:
    """Interval starts after the previous display refresh completes; no catch-up queue."""
    def __init__(self):
        self.running = False
        self.next_due = None
        self.interval = 30
        self.direction = 1

    def start(self, now, interval, direction=1):
        if not 1 <= interval <= 86400 or direction not in (-1, 1):
            raise ValueError("Intervalo: 1–86400 segundos; dirección +1 o -1")
        self.interval, self.direction, self.running = interval, direction, True
        self.completed(now)

    def stop(self):
        self.running = False; self.next_due = None

    def due(self, now):
        return self.running and self.next_due is not None and now >= self.next_due

    def completed(self, now):
        self.next_due = now + self.interval if self.running else None


def real_transport(port):
    import serial
    # Configure before open: don't deliberately pulse DTR/RTS or reset the ESP32.
    transport = serial.Serial(port=None, baudrate=115200, timeout=0.15, write_timeout=2)
    transport.dtr = False
    transport.rts = False
    transport.port = port
    transport.open()
    return transport


def ports():
    from serial.tools import list_ports
    return [(item.device, item.description) for item in list_ports.comports()]


def display_pending(status):
    return bool(status.get("refreshing") or status.get("refreshPending"))


class ControllerWorker(threading.Thread):
    """One owner of the port, one command at a time. All Tk communication uses a queue."""
    def __init__(self, port, events, factory=real_transport, clock=time.monotonic, wall_clock=time.time):
        super().__init__(daemon=True, name="InkCalendarUSB")
        self.port, self.events, self.factory, self.clock = port, events, factory, clock
        self.wall_clock = wall_clock
        self.requests = queue.Queue(maxsize=1)
        self.stopping = threading.Event()
        self.lock = threading.Lock()
        self.connected = False
        self.action_pending = False
        self.auto_config = None
        self.auto_revision = 0
        self.session = None
        self.status = {}

    def emit(self, kind, value):
        self.events.put((kind, value))

    def submit(self, verb, argument=""):
        validate_action(verb, argument)
        with self.lock:
            if not self.connected or self.action_pending or self.stopping.is_set() or self.auto_config:
                return False
            self.action_pending = True
            self.requests.put_nowait((verb, argument))
            return True

    def start_auto(self, interval, direction=1):
        AutoAdvance().start(0, interval, direction)
        with self.lock:
            if not self.connected or self.stopping.is_set() or self.action_pending:
                return False
            self.auto_config = (interval, direction)
            self.auto_revision += 1
        self.emit("auto", True)
        return True

    def stop_auto(self):
        with self.lock:
            self.auto_config = None
            self.auto_revision += 1
        self.emit("auto", False)

    def disconnect(self):
        self.stop_auto()
        self.stopping.set()

    def publish(self, status):
        self.status = status
        self.emit("status", status)

    def await_display(self, status):
        deadline = self.clock() + 120
        while display_pending(status):
            self.emit("busy", True)
            if self.clock() > deadline:
                raise TimeoutError("El refresco lleva más de dos minutos. Avance detenido; revisa BUSY, alimentación y conexión de pantalla.")
            time.sleep(0.1)
            status = self.session.request("status")
            self.publish(status)
        return status

    def perform(self, verb, argument=""):
        self.emit("busy", True)
        try:
            self.emit("log", f"USB → {verb} {argument}".rstrip())
            status = (self.session.synchronize_time(self.wall_clock) if verb == "sync_time"
                      else self.session.request(verb, argument))
            self.publish(status)
            status = self.await_display(status)
            if verb in {"exit", "sync_time"} and not self.stopping.is_set():
                self.publish(self.session.request("hold"))
            if verb == "sync_time":
                self.emit("time_synced", status)
        finally:
            self.emit("busy", False)

    def run(self):
        transport, ready, healthy = None, False, True
        try:
            transport = self.factory(self.port)
            self.session = SerialSession(transport, lambda line: self.emit("log", line), self.clock)
            self.emit("connection_stage", "Puerto abierto; esperando respuesta del calendario… Puedes cancelar con Desconectar.")
            self.publish(self.session.handshake(cancelled=self.stopping.is_set))
            if self.stopping.is_set():
                return
            self.emit("connection_stage", "Calendario detectado; activando la retención de reposo por USB…")
            self.publish(self.session.request("hold", cancelled=self.stopping.is_set))
            ready = True
            self.emit("connection_stage", "Reposo suspendido por USB; esperando a que termine la pantalla…")
            self.await_display(self.status)
            self.emit("busy", False)
            if self.stopping.is_set():
                return
            with self.lock:
                self.connected = True
            self.emit("connected", self.port)
            scheduler, revision = AutoAdvance(), -1
            next_poll, next_hold = self.clock(), self.clock() + 30
            while not self.stopping.is_set():
                with self.lock:
                    config, current_revision = self.auto_config, self.auto_revision
                if revision != current_revision:
                    revision = current_revision
                    if config:
                        scheduler.start(self.clock(), *config)
                    else:
                        scheduler.stop()
                try:
                    verb, argument = self.requests.get(timeout=0.05)
                except queue.Empty:
                    verb = None
                if self.stopping.is_set():
                    break
                if verb:
                    try:
                        self.perform(verb, argument)
                    finally:
                        with self.lock:
                            self.action_pending = False
                    next_poll = self.clock() + 2
                now = self.clock()
                if scheduler.due(now):
                    # Stop is read again immediately before a write, not just on the next UI tick.
                    with self.lock:
                        allowed = self.auto_config is not None and self.auto_revision == revision
                    if allowed and not self.stopping.is_set():
                        self.perform("step", str(scheduler.direction))
                        scheduler.completed(self.clock())
                        self.emit("countdown", scheduler.interval)
                if self.clock() >= next_hold:
                    self.publish(self.session.request("hold"))
                    next_hold = self.clock() + 30
                if self.clock() >= next_poll:
                    self.publish(self.session.request("status"))
                    self.await_display(self.status)
                    next_poll = self.clock() + 2
                if scheduler.running:
                    self.emit("countdown", max(0, scheduler.next_due - self.clock()))
        except ConnectionCancelled:
            healthy = False
            self.emit("log", "Conexión cancelada; puerto liberado. Si llegó la retención USB, caduca como máximo en cinco minutos.")
        except Exception as error:
            healthy = False
            self.emit("error", str(error))
        finally:
            self.stop_auto()
            with self.lock:
                self.connected = False
            # On timeout / unknown command outcome do not enqueue further mutations.
            # The device's bounded five-minute lease provides the crash/cable-loss fallback.
            if ready and healthy:
                try:
                    self.perform("exit")
                except Exception as error:
                    self.emit("log", f"No se pudo restaurar el modo normal: {error}. Caducará la sesión USB en cinco minutos.")
            if transport is not None:
                transport.close()
            self.emit("busy", False)
            self.emit("disconnected", None)


class DemoTransport:
    """Explicit simulator; never opens a COM port and never imports firmware/user images."""
    def __init__(self, port="DEMO", refresh_seconds=0.6, clock=time.monotonic):
        self.clock, self.refresh_seconds = clock, refresh_seconds
        self.output = bytearray()
        self.closed = False
        self.finish = 0
        self.status = dict(usbProtocolVersion=1, wifiMode="off", wifiDriverMode=0, hasCalendar=True,
            board="DEMO · sin dispositivo", today=date.today().isoformat(), selectedDate=date.today().isoformat(),
            displayDate=date.today().isoformat(), displayView="DAILY_CUSTOM", usbPreview=False, refreshPending=False,
            refreshing=False, refreshCount=0, lastRefreshMs=0, clockNeedsSync=False, secondOfDay=12*3600,
            freeHeap=180000, minFreeHeap=170000, phrase="Frase de ejemplo (demo)", phraseCount=6, imageCount=4,
            phraseIndex=0, imageIndex=0, imageName="demo.epd", assetsVersion="DEMO", buttonsPressed=0,
            controlsMode="center_only", rangeStart="2026-01-01", rangeEnd="2027-12-31", usbAwakeSeconds=300,
            deepSleepEnabled=True, lastSync="DEMO", uptimeMs=0, utcEpoch=int(time.time()), clockSource="demo")

    @property
    def in_waiting(self):
        return len(self.output)

    def write(self, data):
        for line in data.decode("ascii").splitlines():
            if line == "status":
                self.output.extend((json.dumps(self._status()) + "\n").encode())
            elif line.startswith("time ") and self.status.get("hasCalendar"):
                epoch = int(line[5:])
                if not 1577836800 <= epoch <= 4102444799:
                    continue
                local = datetime.fromtimestamp(epoch).astimezone()
                self.status.update(utcEpoch=epoch, today=local.date().isoformat(),
                    selectedDate=local.date().isoformat(), secondOfDay=local.hour*3600+local.minute*60+local.second,
                    clockSource="usb", clockNeedsSync=False, refreshPending=True)
                self.finish = self.clock() + self.refresh_seconds
            elif line.startswith("usb "):
                parts = line.split()
                request_id, verb = int(parts[1]), parts[2]
                argument = parts[3] if len(parts) == 4 else ""
                command_bytes(request_id, verb, argument)
                if verb in {"date", "step", "view"}:
                    self.status["usbPreview"] = True
                    if verb == "date": self.status["displayDate"] = argument
                    if verb == "step": self.status["displayDate"] = (date.fromisoformat(self.status["displayDate"]) + timedelta(days=int(argument))).isoformat()
                    if verb == "view": self.status["displayView"] = argument
                if verb == "exit":
                    self.status["usbPreview"] = False
                    self.status["displayDate"] = self.status["today"]
                if verb in {"date", "step", "view", "exit", "refresh"}:
                    self.finish = self.clock() + self.refresh_seconds
                    self.status["refreshPending"] = True
                self.output.extend((json.dumps(dict(usb=1, id=request_id, ok=True, status=self._status())) + "\n").encode())
        return len(data)

    def _status(self):
        if self.status["refreshPending"] and self.clock() >= self.finish:
            self.status["refreshPending"] = False
            self.status["refreshCount"] += 1
            self.status["lastRefreshMs"] = int(self.refresh_seconds * 1000)
        return dict(self.status)

    def read(self, size):
        if not self.output:
            time.sleep(0.005)
        value = bytes(self.output[:size]); del self.output[:size]
        return value

    def close(self):
        self.closed = True
