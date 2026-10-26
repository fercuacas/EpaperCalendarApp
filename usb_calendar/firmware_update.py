"""Explicit local build and app-only USB update. Never erase/partition/filesystem uploads."""
import configparser
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import threading

PROFILES = {"XIAO ESP32-C3": "seeed_xiao_esp32c3", "ESP32-C3 Super Mini": "esp32c3_supermini"}


def python_executable():
    executable = Path(sys.executable)
    console = executable.with_name("python.exe")
    return str(console if executable.name.lower() == "pythonw.exe" and console.exists() else executable)


def digest(data):
    return hashlib.sha256(data).hexdigest()


class FirmwareProject:
    def __init__(self, root):
        self.root = Path(root).expanduser().resolve()
        self.phrases_path = self.root / "assets/phrases/love_phrases.json"
        self.rules_path = self.root / "assets/personal_content.json"
        for path in (self.root / "firmware/platformio.ini", self.root / "tools/generate_personal_assets.py",
                     self.phrases_path, self.rules_path):
            if not path.is_file():
                raise ValueError(f"No es la raíz de Ink_calendar32: falta {path.relative_to(self.root)}")

    def generator(self):
        spec = importlib.util.spec_from_file_location("ink_usb_assets", self.root / "tools/generate_personal_assets.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def catalog(self):
        generator = self.generator()
        phrases, images, skipped = generator.load_assets(self.root)
        schedule = generator.load_schedule(self.root, images)
        return dict(version=generator.assets_fingerprint(phrases, images, schedule), phrases=len(phrases),
                    images=len(images), specialPhrases=len(schedule["specialPhrases"]),
                    specialImages=len(schedule["specialImages"]), skipped=skipped)

    def load_editor(self):
        original = {path: path.read_bytes() for path in (self.phrases_path, self.rules_path)}
        source = json.loads(original[self.phrases_path].decode("utf-8"))
        if not isinstance(source, dict) or not isinstance(source.get("phrases"), list) or not all(isinstance(value, str) for value in source["phrases"]):
            raise ValueError('love_phrases.json debe contener un objeto {"phrases": ["Texto", ...]}')
        return source, "\n".join(source["phrases"]), original[self.rules_path].decode("utf-8"), original

    def save_editor(self, phrases_text, rules_text, original):
        source = json.loads(original[self.phrases_path].decode("utf-8"))
        source["phrases"] = [line.strip() for line in phrases_text.splitlines() if line.strip()]
        rules = json.loads(rules_text)
        phrases_json = json.dumps(source, ensure_ascii=False, indent=2) + "\n"
        rules_json = json.dumps(rules, ensure_ascii=False, indent=2) + "\n"
        # Validate BOTH drafts against the real available EPDs before touching either source.
        generator = self.generator()
        with tempfile.TemporaryDirectory(prefix="ink-usb-editor-") as directory:
            fixture = Path(directory)
            (fixture / "assets/phrases").mkdir(parents=True)
            (fixture / "assets/images").mkdir()
            for image in (self.root / "assets/images").glob("*.epd"):
                shutil.copyfile(image, fixture / "assets/images" / image.name)
            (fixture / "assets/phrases/love_phrases.json").write_text(phrases_json, encoding="utf-8")
            (fixture / "assets/personal_content.json").write_text(rules_json, encoding="utf-8")
            generator.load_assets(fixture)
        for path, previous in original.items():
            if path.read_bytes() != previous:
                raise ValueError("Los archivos han cambiado en otro editor. Cierra y vuelve a abrir este editor para no sobrescribirlos.")
        for path, content in ((self.phrases_path, phrases_json), (self.rules_path, rules_json)):
            if content.encode("utf-8") == original[path]:
                continue
            fd, temporary = tempfile.mkstemp(prefix=path.name + ".usb-", dir=path.parent)
            try:
                with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as output:
                    output.write(content)
                    output.flush()
                    os.fsync(output.fileno())
                os.replace(temporary, path)
            finally:
                if os.path.exists(temporary):
                    os.unlink(temporary)
        return self.load_editor()

    def validate_profile(self, profile):
        if profile not in PROFILES.values():
            raise ValueError("Perfil de placa no permitido")
        config = configparser.ConfigParser(interpolation=None)
        try:
            config.read(self.root / "firmware/platformio.ini", encoding="utf-8")
        except configparser.Error as error:
            raise ValueError("No se puede leer platformio.ini: " + str(error)) from error
        section = "env:" + profile
        if section not in config:
            raise ValueError("El proyecto no contiene ese perfil")
        if profile == "seeed_xiao_esp32c3":
            flags = config[section].get("build_flags", "")
            if not re.search(r"-D\s*INK_WIFI_DISABLED\s*=\s*1(?:\s|$)", flags):
                raise ValueError("Seguridad: el perfil XIAO debe mantener INK_WIFI_DISABLED=1 mientras no tenga antena")
            if re.search(r"-D\s*INK_WIFI_DISABLED\s*=\s*0(?:\s|$)|-U\s*INK_WIFI_DISABLED", flags):
                raise ValueError("Configuración contradictoria de bloqueo Wi-Fi")


def application_partition(table):
    if len(table) < 32 or len(table) > 4096 or len(table) % 32:
        raise ValueError("Tabla de particiones compilada inválida")
    for offset in range(0, len(table), 32):
        magic, kind, subtype, address, size = struct.unpack_from("<HBBII", table, offset)
        if magic == 0x50aa and kind == 0 and subtype == 0x10:
            if address != 0x10000 or not size or address + size > 4 * 1024 * 1024:
                raise ValueError("Esta herramienta solo actualiza app0 en 0x10000 dentro de flash de 4 MB")
            return address, size
    raise ValueError("No se encontró app0; no se permite cambiar las particiones")


def pio_command():
    if shutil.which("pio"):
        return [shutil.which("pio")]
    if importlib.util.find_spec("platformio"):
        return [python_executable(), "-m", "platformio"]
    raise ValueError("Falta PlatformIO. Instálalo en el Python de esta herramienta: python -m pip install platformio")


def esptool_script():
    core = Path(os.environ.get("PLATFORMIO_CORE_DIR", str(Path.home() / ".platformio")))
    script = core / "packages/tool-esptoolpy/esptool.py"
    if not script.is_file():
        raise ValueError("No se encuentra esptool de PlatformIO. Compila primero el perfil del proyecto con PlatformIO.")
    return script


def run_command(arguments, cwd, emit, timeout=900):
    emit("build_log", "Ejecutando: " + subprocess.list2cmdline([str(item) for item in arguments]))
    process = subprocess.Popen([str(item) for item in arguments], cwd=str(cwd), shell=False,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    def read_output():
        for line in process.stdout:
            emit("build_log", line.rstrip()[:2000])
    reader = threading.Thread(target=read_output, daemon=True)
    reader.start()
    try:
        code = process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], shell=False,
                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0), capture_output=True)
        else:
            process.kill()
        process.wait()
        raise TimeoutError("El proceso superó el tiempo máximo. No se continuará con otra fase.")
    finally:
        reader.join(5)
        process.stdout.close()
    if code:
        raise RuntimeError(f"El proceso terminó con código {code}. Consulta el registro; no se continúa con la carga.")


class FirmwareUpdate(threading.Thread):
    """Park in ROM loader to prevent sleep during compilation, then update app only."""
    def __init__(self, project, profile, port, events, controller=None, android_output=None, runner=run_command):
        super().__init__(name="InkFirmwareUpdate", daemon=False)
        self.project, self.profile, self.port = project, profile, port
        self.events, self.controller, self.android_output, self.runner = events, controller, android_output, runner
        self.phase = "starting"

    def emit(self, kind, value):
        self.events.put((kind, value))

    def stage(self, phase, text):
        self.phase = phase
        self.emit("build_stage", text)

    def execute(self):
        if not re.fullmatch(r"COM[1-9][0-9]*|/dev/(?:tty|cu\.)[A-Za-z0-9._-]+", self.port):
            raise ValueError("Selecciona un puerto USB real; no se puede cargar al modo DEMO")
        project = self.project
        project.validate_profile(self.profile)
        before = project.catalog()
        pio = pio_command()
        tool = esptool_script()
        base = [python_executable(), "-B", str(tool), "--chip", "esp32c3", "--port", self.port, "--baud", "460800"]
        self.stage("releasing", "Liberando el puerto serie; esperando a que termine el refresco…")
        if self.controller is not None:
            self.controller.disconnect()
            self.controller.join(120)
            if self.controller.is_alive():
                raise RuntimeError("El controlador no ha liberado el puerto; no se inicia la carga")
        self.stage("parking", "Poniendo la placa en modo de carga USB para que no se duerma durante la compilación…")
        self.runner(base + ["--before", "default_reset", "--after", "no_reset", "chip_id"], project.root, self.emit, timeout=120)
        self.stage("building", f"Compilando: {before['phrases']} frases, {before['specialPhrases']} fechas de frase, "
                   f"{before['images']} fotos y {before['specialImages']} fechas de foto…")
        for warning in before["skipped"]:
            self.emit("build_log", "Recurso omitido: " + warning)
        self.runner(pio + ["run", "--project-dir", str(project.root / "firmware"), "-e", self.profile], project.root, self.emit)
        after = project.catalog()
        if before["version"] != after["version"]:
            raise ValueError("Las frases/fotos/reglas cambiaron durante la compilación. Vuelve a compilar; no se ha cargado nada.")
        project.validate_profile(self.profile)
        build = project.root / "firmware/.pio/build" / self.profile
        header = (build / "generated/PersonalAssets.generated.h").read_text(encoding="utf-8")
        if f'fingerprint[] = "{after["version"]}"' not in header:
            raise ValueError("El binario no corresponde al catálogo actual; no se permite cargar una versión anterior")
        table = (build / "partitions.bin").read_bytes()
        address, capacity = application_partition(table)
        firmware = build / "firmware.bin"
        image = firmware.read_bytes()
        if not 24 < len(image) <= capacity or image[0] != 0xe9 or after["version"].encode("ascii") not in image:
            raise ValueError("El programa compilado no es válido o no cabe en app0")
        image_digest = digest(image)
        if self.android_output:
            project.generator().export_android(project.root, Path(self.android_output))
            self.emit("build_log", "Catálogo Android regenerado; para verlo en el móvil aún hay que compilar e instalar el APK.")
        self.stage("checking", "Comprobando particiones en el dispositivo (solo lectura). Si duerme, pulsa el central y reintenta.")
        with tempfile.TemporaryDirectory(prefix="ink-usb-update-") as directory:
            device_table = Path(directory) / "device-partitions.bin"
            self.runner(base + ["--before", "no_reset", "--after", "no_reset", "read_flash", "0x8000", str(len(table)), str(device_table)],
                        project.root, self.emit, timeout=120)
            if device_table.read_bytes() != table:
                raise ValueError("Las particiones del dispositivo no coinciden. NO se ha escrito el programa. "
                                 "La placa puede quedar en modo BOOT; esta opción no cambia particiones ni sirve para una primera instalación.")
            if digest(firmware.read_bytes()) != image_digest or project.catalog()["version"] != after["version"]:
                raise ValueError("Los archivos han cambiado antes de la carga. No se escribe nada; vuelve a compilar.")
            self.stage("flashing", "Cargando solo app0. No desconectes el USB ni cierres la aplicación…")
            self.runner(base + ["--before", "no_reset", "--after", "hard_reset", "write_flash", "-z",
                                "--flash_mode", "dio", "--flash_freq", "80m", "--flash_size", "4MB", hex(address), str(firmware)],
                        project.root, self.emit, timeout=300)
        self.stage("finished", "Programa cargado y verificado por esptool. Calendario y preferencias no se han sobrescrito. "
                   "Espera al arranque y pulsa Conectar; si duerme, pulsa primero el central.")
        return after

    def run(self):
        try:
            result = self.execute()
            self.emit("build_done", result)
        except Exception as error:
            suffix = ""
            if self.phase not in {"starting", "releasing"}:
                suffix = " La placa puede seguir en modo BOOT. Si hubo un corte al escribir, vuelve a cargar antes de usarla."
            self.emit("build_error", str(error) + suffix)
