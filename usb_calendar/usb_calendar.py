"""Ink Calendar USB: control, edición de contenido y actualización explícita por USB."""
import argparse
from datetime import date, datetime
import json
import queue
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, scrolledtext, ttk

try:
    from .controller import ControllerWorker, DemoTransport, VIEWS, ports
    from .firmware_update import FirmwareProject, FirmwareUpdate, PROFILES
except ImportError:
    from controller import ControllerWorker, DemoTransport, VIEWS, ports
    from firmware_update import FirmwareProject, FirmwareUpdate, PROFILES

VIEW_LABELS = dict(zip(("Día", "Semana", "Mes", "Personalizada · frase y foto"), VIEWS))


class UsbCalendarApp:
    def __init__(self, root, demo=False):
        self.root, self.worker = root, None
        self.events = queue.Queue()
        self.last_status = {}
        self.connected = self.busy = self.automatic = self.closing = False
        self.connection_error = None
        self.updating = False
        self.updater = self.editor = None
        self.project_root = tk.StringVar(value="")
        self.profile = tk.StringVar(value="XIAO ESP32-C3")
        self.android_catalog = tk.BooleanVar(value=True)
        self.port = tk.StringVar(value="DEMO — sin dispositivo" if demo else "")
        self.target_date = tk.StringVar(value=date.today().isoformat())
        self.view = tk.StringVar(value="Personalizada · frase y foto")
        self.interval = tk.StringVar(value="30")
        self.direction = tk.StringVar(value="Avanzar +1 día")
        self.connection = tk.StringVar(value="Desconectado")
        self.activity = tk.StringVar(value="Conecta por USB. Si está dormido, pulsa brevemente el botón central primero.")
        self.countdown = tk.StringVar(value="Avance automático detenido")
        root.title("Ink Calendar · Control USB")
        root.geometry("1120x800")
        root.minsize(960, 760)
        root.protocol("WM_DELETE_WINDOW", self.close)
        style = ttk.Style(root)
        style.theme_use("clam")
        style.configure("TFrame", background="#f5f7fb")
        style.configure("TLabel", background="#f5f7fb", foreground="#26364a", font=("Segoe UI", 10))
        style.configure("TButton", font=("Segoe UI", 10), padding=(10, 5))
        style.configure("Title.TLabel", font=("Segoe UI", 21, "bold"), foreground="#123960")
        style.configure("Muted.TLabel", foreground="#5c6c80", font=("Segoe UI", 9))
        style.configure("TLabelframe", background="#f5f7fb")
        style.configure("TLabelframe.Label", background="#f5f7fb", font=("Segoe UI", 10, "bold"))
        container = ttk.Frame(root, padding=18)
        container.pack(fill="both", expand=True)
        container.columnconfigure(0, weight=1)
        container.rowconfigure(3, weight=1)
        ttk.Label(container, text="Ink Calendar · USB", style="Title.TLabel").grid(row=0, column=0, sticky="w")
        ttk.Label(container, text="Control local sin Wi-Fi · las fechas de prueba no cambian el reloj ni tus eventos",
                  style="Muted.TLabel").grid(row=1, column=0, sticky="w", pady=(0, 12))

        connection = ttk.LabelFrame(container, text="Conexión", padding=10)
        connection.grid(row=2, column=0, sticky="ew")
        connection.columnconfigure(0, weight=1)
        self.port_box = ttk.Combobox(connection, textvariable=self.port, width=48)
        self.port_box.grid(row=0, column=0, sticky="ew", padx=(0, 8))
        self.scan_button = ttk.Button(connection, text="Buscar puertos", command=self.scan)
        self.scan_button.grid(row=0, column=1, padx=4)
        self.connect_button = ttk.Button(connection, text="Conectar", command=self.toggle_connection)
        self.connect_button.grid(row=0, column=2, padx=(8, 0))
        ttk.Label(connection, textvariable=self.connection, style="Muted.TLabel").grid(row=1, column=0, columnspan=3, sticky="w", pady=(7, 0))

        middle = ttk.Frame(container)
        middle.grid(row=3, column=0, sticky="nsew", pady=12)
        middle.columnconfigure(1, weight=1)
        middle.rowconfigure(0, weight=1)
        left = ttk.Frame(middle)
        left.grid(row=0, column=0, sticky="nsw", padx=(0, 15))
        left.columnconfigure(0, weight=1)
        left.rowconfigure(0, weight=1)
        control_tabs = ttk.Notebook(left)
        control_tabs.grid(row=0, column=0, sticky="nsew")
        navigation_page, automatic_page = ttk.Frame(control_tabs, padding=4), ttk.Frame(control_tabs, padding=4)
        control_tabs.add(navigation_page, text="Navegar")
        control_tabs.add(automatic_page, text="Automático")
        update_page = ttk.Frame(control_tabs, padding=12)
        control_tabs.add(update_page, text="Actualizar")
        update_page.columnconfigure(0, weight=1)
        ttk.Label(update_page, text="Carpeta del proyecto Ink_calendar32").grid(row=0, column=0, columnspan=2, sticky="w")
        self.project_entry = ttk.Entry(update_page, textvariable=self.project_root, width=31)
        self.project_entry.grid(row=1, column=0, sticky="ew", pady=(6, 8))
        self.project_button = ttk.Button(update_page, text="…", width=3, command=self.choose_project)
        self.project_button.grid(row=1, column=1, padx=(5, 0), pady=(6, 8))
        self.profile_box = ttk.Combobox(update_page, textvariable=self.profile, values=list(PROFILES), state="readonly", width=31)
        self.profile_box.grid(row=2, column=0, columnspan=2, sticky="ew", pady=(0, 8))
        self.edit_button = ttk.Button(update_page, text="Editar frases y fechas…", command=self.edit_content)
        self.edit_button.grid(row=3, column=0, columnspan=2, sticky="ew", pady=(0, 8))
        self.catalog_check = ttk.Checkbutton(update_page, text="Regenerar también el catálogo Android\n(no compila ni instala el APK)", variable=self.android_catalog)
        self.catalog_check.grid(row=4, column=0, columnspan=2, sticky="w", pady=(0, 8))
        self.update_button = ttk.Button(update_page, text="Compilar y cargar por USB…", command=self.start_update)
        self.update_button.grid(row=5, column=0, columnspan=2, sticky="ew")
        ttk.Label(update_page, text="Incluye frases, fotos .epd y sus fechas guardadas.\nSolo actualiza el programa: no borra los eventos.\nXIAO mantiene el Wi-Fi bloqueado.\nDespierta el XIAO con el central antes de empezar.",
                  style="Muted.TLabel", wraplength=340).grid(row=6, column=0, columnspan=2, sticky="w", pady=(8, 0))
        navigate = ttk.LabelFrame(navigation_page, text="Fecha de prueba", padding=12)
        navigate.grid(row=0, column=0, sticky="ew")
        ttk.Label(navigate, text="Fecha (AAAA-MM-DD)").grid(row=0, column=0, columnspan=2, sticky="w")
        self.date_entry = ttk.Entry(navigate, textvariable=self.target_date, width=19, font=("Segoe UI", 13))
        self.date_entry.grid(row=1, column=0, sticky="ew", pady=8)
        self.date_entry.bind("<Return>", lambda event: self.go_date())
        self.date_button = ttk.Button(navigate, text="Mostrar fecha", command=self.go_date)
        self.date_button.grid(row=1, column=1, padx=(8, 0))
        arrows = ttk.Frame(navigate)
        arrows.grid(row=2, column=0, columnspan=2, sticky="ew", pady=3)
        self.day_buttons = []
        for index, (label, days) in enumerate((("−7 días", -7), ("← Anterior", -1), ("Siguiente →", 1), ("+7 días", 7))):
            button = ttk.Button(arrows, text=label, width=9, command=lambda n=days: self.send("step", str(n)))
            button.grid(row=0, column=index, padx=2, sticky="ew")
            arrows.columnconfigure(index, weight=1)
            self.day_buttons.append(button)
        self.today_button = ttk.Button(navigate, text="Hoy (en pruebas)", command=self.go_today)
        self.today_button.grid(row=3, column=0, columnspan=2, sticky="ew", pady=(7, 0))
        self.sync_time_button = ttk.Button(navigate, text="Sincronizar fecha con el PC…", command=self.sync_time)
        self.sync_time_button.grid(row=4, column=0, columnspan=2, sticky="ew", pady=(7, 0))
        ttk.Label(navigate, text="Actualiza fecha y hora reales y sale de las pruebas.\nNo cambia eventos ni activa Wi-Fi.",
                  style="Muted.TLabel").grid(row=5, column=0, columnspan=2, sticky="w", pady=(5, 0))

        views = ttk.LabelFrame(navigation_page, text="Vista de pantalla", padding=12)
        views.grid(row=1, column=0, sticky="ew", pady=10)
        self.view_box = ttk.Combobox(views, textvariable=self.view, values=list(VIEW_LABELS), state="readonly", width=29)
        self.view_box.grid(row=0, column=0, sticky="ew", padx=(0, 8))
        self.view_button = ttk.Button(views, text="Aplicar", command=lambda: self.send("view", VIEW_LABELS[self.view.get()]))
        self.view_button.grid(row=0, column=1)

        automatic = ttk.LabelFrame(automatic_page, text="Recorrer días automáticamente", padding=12)
        automatic.grid(row=0, column=0, sticky="ew")
        settings = ttk.Frame(automatic)
        settings.grid(row=0, column=0, columnspan=2, sticky="ew")
        ttk.Label(settings, text="Esperar").pack(side="left")
        self.interval_box = ttk.Spinbox(settings, from_=1, to=86400, textvariable=self.interval, width=7)
        self.interval_box.pack(side="left", padx=8)
        ttk.Label(settings, text="segundos").pack(side="left")
        ttk.Label(automatic, text="El intervalo empieza al finalizar cada refresco.", style="Muted.TLabel").grid(row=1, column=0, columnspan=2, sticky="w", pady=6)
        self.direction_box = ttk.Combobox(automatic, textvariable=self.direction,
            values=("Avanzar +1 día", "Retroceder -1 día"), state="readonly", width=29)
        self.direction_box.grid(row=2, column=0, columnspan=2, sticky="ew", pady=(0, 8))
        self.start_button = ttk.Button(automatic, text="▶ Iniciar", command=self.start_auto)
        self.start_button.grid(row=3, column=0, sticky="ew", padx=(0, 6))
        self.stop_button = ttk.Button(automatic, text="■ PARAR", command=self.stop_auto)
        self.stop_button.grid(row=3, column=1, sticky="ew")
        ttk.Label(automatic, textvariable=self.countdown, style="Muted.TLabel").grid(row=4, column=0, columnspan=2, sticky="w", pady=(8, 0))
        self.redraw_button = ttk.Button(left, text="Volver a dibujar la pantalla", command=lambda: self.send("refresh"))
        self.redraw_button.grid(row=1, column=0, sticky="ew", pady=(8, 4))
        self.exit_button = ttk.Button(left, text="Volver al calendario normal", command=lambda: self.send("exit"))
        self.exit_button.grid(row=2, column=0, sticky="ew")
        ttk.Label(left, text="USB lo mantiene despierto. Desconecta para liberar reposo.\nPARAR no cancela un refresco ya iniciado.",
                  style="Muted.TLabel", justify="left").grid(row=3, column=0, sticky="w", pady=(8, 0))

        notebook = ttk.Notebook(middle)
        notebook.grid(row=0, column=1, sticky="nsew")
        status_tab = ttk.Frame(notebook, padding=12)
        content_tab = ttk.Frame(notebook, padding=12)
        json_tab = ttk.Frame(notebook, padding=4)
        notebook.add(status_tab, text="Estado")
        notebook.add(content_tab, text="Frase y foto")
        notebook.add(json_tab, text="JSON")
        self.values = {}
        status_tab.columnconfigure(1, weight=1)
        labels = ("Modo", "Fecha mostrada", "Hoy del reloj", "Vista", "Pantalla", "Último refresco", "Refrescos",
                  "Reloj", "Radio Wi-Fi", "Placa / botones", "Memoria libre", "Reposo", "Rango de eventos", "Batería", "Última sincronización")
        for row, label in enumerate(labels):
            ttk.Label(status_tab, text=label, style="Muted.TLabel").grid(row=row, column=0, sticky="nw", padx=(0, 12), pady=2)
            value = tk.StringVar(value="—")
            self.values[label] = value
            ttk.Label(status_tab, textvariable=value, wraplength=360).grid(row=row, column=1, sticky="nw", pady=2)
        self.content = tk.StringVar(value="Conecta para consultar la frase y el nombre de la foto.")
        ttk.Label(content_tab, textvariable=self.content, wraplength=460, justify="left").pack(anchor="nw")
        self.json_text = scrolledtext.ScrolledText(json_tab, state="disabled", font=("Consolas", 9), width=40, wrap="none")
        self.json_text.pack(fill="both", expand=True)
        footer = ttk.Frame(container)
        footer.grid(row=4, column=0, sticky="ew")
        footer.columnconfigure(0, weight=1)
        ttk.Label(footer, textvariable=self.activity, style="Muted.TLabel", wraplength=600).grid(row=0, column=0, sticky="w")
        self.global_stop_button = ttk.Button(footer, text="■ PARAR", command=self.stop_auto)
        self.global_stop_button.grid(row=0, column=1, padx=(8, 0))
        ttk.Button(footer, text="Guardar diagnóstico…", command=self.export).grid(row=0, column=2, padx=(8, 0))
        self.log = scrolledtext.ScrolledText(container, height=4, state="disabled", font=("Consolas", 9), wrap="word")
        self.log.grid(row=5, column=0, sticky="ew", pady=(8, 0))
        self.port_box.bind("<<ComboboxSelected>>", lambda event: self.update_controls())
        self.port_box.bind("<KeyRelease>", lambda event: self.update_controls())
        self.scan()
        self.update_controls()
        root.after(100, self.pump)

    def scan(self):
        try:
            choices = [f"{port} — {description}" for port, description in ports()]
        except ImportError:
            choices = []
            self.activity.set("Falta pyserial: instala usb_calendar/requirements.txt. El modo DEMO sigue disponible.")
        choices.append("DEMO — sin dispositivo")
        self.port_box["values"] = choices
        if not self.port.get():
            self.port.set(choices[0])

    def update_controls(self):
        ready = self.connected and not self.busy and not self.automatic and not self.closing and not self.updating
        for widget in [self.date_button, self.today_button, self.sync_time_button, self.view_button, self.redraw_button, self.exit_button, *self.day_buttons]:
            widget.configure(state="normal" if ready else "disabled")
        self.start_button.configure(state="normal" if ready else "disabled")
        self.stop_button.configure(state="normal" if self.automatic and not self.closing else "disabled")
        self.global_stop_button.configure(state="normal" if self.automatic and not self.closing else "disabled")
        for widget in (self.date_entry, self.interval_box):
            widget.configure(state="normal" if not self.automatic and not self.closing and not self.updating else "disabled")
        self.view_box.configure(state="readonly" if not self.automatic and not self.closing and not self.updating else "disabled")
        self.direction_box.configure(state="readonly" if not self.automatic and not self.closing and not self.updating else "disabled")
        self.port_box.configure(state="disabled" if self.worker or self.updating else "normal")
        self.scan_button.configure(state="disabled" if self.worker or self.updating else "normal")
        self.connect_button.configure(text="Desconectar" if self.worker else "Conectar", state="disabled" if self.closing or self.updating else "normal")
        for widget in (self.project_entry, self.project_button, self.edit_button, self.catalog_check):
            widget.configure(state="disabled" if self.updating or self.closing else "normal")
        self.profile_box.configure(state="disabled" if self.updating or self.closing else "readonly")
        real_port = bool(self.port.get().strip()) and not self.port.get().startswith("DEMO")
        self.update_button.configure(state="normal" if real_port and not self.updating and not self.closing else "disabled")

    def toggle_connection(self):
        if self.updating:
            return
        if self.worker:
            self.worker.disconnect()
            self.activity.set("Desconectando: esperando al refresco y restaurando el modo normal…")
            return
        port = self.port.get().split(" — ")[0].strip()
        if not port:
            return
        factory = DemoTransport if port == "DEMO" else None
        self.worker = ControllerWorker(port, self.events, **({"factory": factory} if factory else {}))
        self.connection_error = None
        self.connection.set("Conectando… Si no responde, pulsa brevemente el central y vuelve a conectar.")
        self.worker.start()
        self.update_controls()

    def send(self, verb, argument=""):
        if not self.worker or self.updating:
            return
        try:
            if self.worker.submit(verb, argument):
                self.busy = True
                self.activity.set("Orden enviada; esperando al refresco de la pantalla…")
                self.update_controls()
        except ValueError as error:
            messagebox.showerror("Revisa los datos", str(error), parent=self.root)

    def go_date(self):
        self.send("date", self.target_date.get().strip())

    def go_today(self):
        self.target_date.set(self.last_status.get("today") or date.today().isoformat())
        self.go_date()

    def sync_time(self):
        if not self.worker or not self.connected or self.busy or self.automatic or self.closing or self.updating:
            return
        now = datetime.now().astimezone().strftime("%d/%m/%Y %H:%M:%S %Z")
        if messagebox.askyesno("Sincronizar fecha y hora", f"Reloj del ordenador: {now}\n\n"
                "¿Actualizar la fecha y hora reales del calendario?\n"
                "Se cerrará la fecha de prueba y volverá al calendario normal.\n"
                "Se conserva la zona horaria configurada en el dispositivo.\n"
                "No se modifican eventos ni se activa Wi-Fi.", parent=self.root):
            self.send("sync_time")

    def start_auto(self):
        if self.updating:
            return
        try:
            seconds = float(self.interval.get().replace(",", "."))
            direction = -1 if self.direction.get().startswith("Retroceder") else 1
            if self.worker and self.worker.start_auto(seconds, direction):
                self.automatic = True
                self.activity.set("Avance automático activo. Puedes pulsar PARAR en cualquier momento.")
                self.update_controls()
        except ValueError as error:
            messagebox.showerror("Intervalo no válido", str(error), parent=self.root)

    def stop_auto(self):
        if self.worker:
            self.worker.stop_auto()
        self.automatic = False
        self.countdown.set("Avance detenido; si hay un refresco iniciado, terminará normalmente.")
        self.update_controls()

    def show_status(self, status):
        self.last_status = status
        seconds = status.get("secondOfDay", 0)
        values = {
            "Modo": "PRUEBA USB · no se guarda" if status.get("usbPreview") else "Calendario normal",
            "Fecha mostrada": status.get("displayDate", "—"),
            "Hoy del reloj": f"{status.get('today', '—')}  {seconds//3600:02}:{seconds%3600//60:02}:{seconds%60:02}",
            "Vista": status.get("displayView", "—"),
            "Pantalla": "Actualización pendiente/en curso" if status.get("refreshPending") or status.get("refreshing") else "En reposo (imagen fija)",
            "Último refresco": f"{status.get('lastRefreshMs', 0)/1000:.1f} s",
            "Refrescos": str(status.get("refreshCount", "—")),
            "Reloj": "Necesita sincronizarse" if status.get("clockNeedsSync") else "Hora confirmada · " + str(status.get("clockSource", "—")),
            "Radio Wi-Fi": f"{status.get('wifiMode', '—')} · driver {status.get('wifiDriverMode', '—')}",
            "Placa / botones": f"{status.get('board', '—')} · máscara {status.get('buttonsPressed', 0)}",
            "Memoria libre": f"{status.get('freeHeap', 0)//1024} KiB · mínimo {status.get('minFreeHeap', 0)//1024} KiB",
            "Reposo": f"Retenido por USB {status.get('usbAwakeSeconds', 0)} s · sueño automático {'sí' if status.get('deepSleepEnabled') else 'no'}",
            "Rango de eventos": f"{status.get('rangeStart', '—')} → {status.get('rangeEnd', '—')}",
            "Batería": "No se mide; el USB no indica porcentaje de carga",
            "Última sincronización": status.get("lastSync", "—")
        }
        for label, value in values.items():
            self.values[label].set(value)
        self.content.set(f"FRASE {'ESPECIAL' if status.get('phraseSpecial') else 'NORMAL'}\n\n{status.get('phrase', '—')}\n\n"
            f"Posición normal: {status.get('phraseIndex', 0)+1}/{status.get('phraseCount', 0)}\n\n"
            f"FOTO {'ESPECIAL' if status.get('imageSpecial') else 'NORMAL'}\n\n{status.get('imageName', '—')}\n"
            f"Índice de catálogo: {status.get('imageIndex', 0)+1}/{status.get('imageCount', 0)}\n\n"
            f"Versión de recursos: {status.get('assetsVersion', '—')}\nAncla: {status.get('contentAnchor', '—')}\n\n"
            "Los índices de foto no son consecutivos: el orden está barajado. Fuera del rango sincronizado no se muestran eventos.")
        self.json_text.configure(state="normal")
        self.json_text.delete("1.0", "end")
        self.json_text.insert("end", json.dumps(status, ensure_ascii=False, indent=2))
        self.json_text.configure(state="disabled")

    def add_log(self, message):
        self.log.configure(state="normal")
        self.log.insert("end", datetime.now().strftime("%H:%M:%S ") + message + "\n")
        if int(self.log.index("end-1c").split(".")[0]) > 500:
            self.log.delete("1.0", "100.0")
        self.log.see("end")
        self.log.configure(state="disabled")

    def pump(self):
        try:
            while True:
                kind, value = self.events.get_nowait()
                if kind == "status": self.show_status(value)
                elif kind == "connection_stage":
                    self.connection.set(value)
                    self.activity.set(value)
                elif kind == "connected":
                    self.connected = True
                    self.connection.set(f"Conectado a {value} · 115200 · sin Wi-Fi")
                    self.activity.set("Listo · reposo suspendido mientras esta conexión USB permanezca activa.")
                elif kind == "disconnected":
                    self.worker = None; self.connected = self.busy = self.automatic = False
                    self.connection.set("Error de conexión: " + self.connection_error if self.connection_error else
                                        "Desconectado · pulsa el central si necesitas despertar el XIAO")
                    if self.closing:
                        self.root.destroy(); return
                elif kind == "busy": self.busy = value
                elif kind == "auto":
                    self.automatic = value
                    if not value: self.countdown.set("Avance automático detenido")
                elif kind == "countdown" and self.automatic:
                    self.countdown.set(f"Próximo día en {value:.1f} s" if not self.busy else "Esperando a que termine el refresco…")
                elif kind == "error":
                    self.connection_error = value
                    self.activity.set(value); self.add_log("ERROR: " + value)
                elif kind == "log": self.add_log(value)
                elif kind == "time_synced":
                    seconds = value.get("secondOfDay", 0)
                    self.target_date.set(value.get("today", ""))
                    message = f"Fecha sincronizada: {value.get('today', '—')} {seconds//3600:02}:{seconds%3600//60:02}:{seconds%60:02} · hora del dispositivo"
                    self.activity.set(message)
                    self.add_log(message)
                elif kind == "build_log": self.add_log(value)
                elif kind == "build_stage":
                    self.activity.set(value)
                    self.add_log(value)
                elif kind == "build_error":
                    self.updating = False
                    self.activity.set("Actualización detenida. Consulta el error en el registro.")
                    self.add_log("ERROR DE ACTUALIZACIÓN: " + value)
                    messagebox.showerror("No se completó la actualización", value, parent=self.root)
                elif kind == "build_done":
                    self.updating = False
                    self.activity.set("Firmware cargado. Espera al arranque y pulsa Conectar para verificarlo.")
                    self.add_log(f"Versión de recursos cargada: {value['version']}")
                self.update_controls()
        except queue.Empty:
            pass
        self.root.after(100, self.pump)

    def export(self):
        path = filedialog.asksaveasfilename(parent=self.root, title="Guardar diagnóstico local", defaultextension=".json",
                                          filetypes=[("JSON", "*.json")], initialfile="ink-calendar-diagnostico.json")
        if path:
            try:
                with open(path, "w", encoding="utf-8") as output:
                    json.dump({"capturedAt": datetime.now().astimezone().isoformat(), "status": self.last_status,
                               "log": self.log.get("1.0", "end").strip()}, output, ensure_ascii=False, indent=2)
            except OSError as error:
                messagebox.showerror("No se pudo guardar", str(error), parent=self.root)

    def close(self):
        if self.updating:
            messagebox.showinfo("Actualización en curso", "Espera a que termine la compilación/carga. No desconectes el USB durante la escritura.", parent=self.root)
            return
        if self.closing:
            # A second close can abandon the UI; device lease is the bounded fallback.
            self.root.destroy(); return
        self.closing = True
        if self.worker:
            self.worker.disconnect()
            self.activity.set("Cerrando conexión sin interrumpir la pantalla. Otro cierre fuerza salir; el plazo USB de seguridad sigue activo.")
            self.update_controls()
        else:
            self.root.destroy()

    def choose_project(self):
        folder = filedialog.askdirectory(parent=self.root, title="Selecciona la raíz de Ink_calendar32")
        if folder:
            self.project_root.set(folder)

    def edit_content(self):
        if self.updating:
            return
        if self.editor is not None:
            self.editor.window.lift()
            return
        try:
            project = FirmwareProject(self.project_root.get())
            self.editor = ContentEditor(self.root, project, lambda: setattr(self, "editor", None))
        except (OSError, ValueError, KeyError) as error:
            messagebox.showerror("No se pudo abrir el editor", str(error), parent=self.root)

    def start_update(self):
        if self.updating:
            return
        if self.editor is not None:
            messagebox.showinfo("Guarda los cambios", "Primero pulsa Guardar y cerrar en el editor de frases y fechas.", parent=self.root)
            return
        try:
            project = FirmwareProject(self.project_root.get())
            profile = PROFILES[self.profile.get()]
            project.validate_profile(profile)
            port = self.port.get().split(" — ")[0].strip()
            if not port or port == "DEMO":
                raise ValueError("Selecciona el puerto USB físico, no DEMO")
            if self.connected:
                board = self.last_status.get("board", "")
                if ("XIAO" in board and profile != "seeed_xiao_esp32c3") or ("Super Mini" in board and profile != "esp32c3_supermini"):
                    raise ValueError("El perfil seleccionado no coincide con la placa conectada")
            if not messagebox.askyesno("Compilar y cargar firmware", f"Placa: {self.profile.get()}\nPuerto: {port}\nProyecto: {project.root}\n\n"
                "Se incluirán todas las frases, fechas especiales y fotos .epd GUARDADAS.\n"
                "El calendario se detendrá en modo BOOT mientras se compila para evitar que se duerma.\n"
                "Se escribirá solo el programa, comprobando primero las particiones. No se borrarán eventos ni ajustes.\n\n"
                "¿Continuar? No desconectes el USB durante la escritura.", parent=self.root):
                return
            self.stop_auto()
            self.updating = True
            self.activity.set("Preparando actualización USB…")
            android_output = Path(__file__).resolve().parents[1] / "app/src/main/assets/personal_content/catalog.json" if self.android_catalog.get() else None
            self.updater = FirmwareUpdate(project, profile, port, self.events, controller=self.worker, android_output=android_output)
            self.update_controls()
            self.updater.start()
        except (OSError, ValueError, KeyError) as error:
            messagebox.showerror("No se puede actualizar", str(error), parent=self.root)


class ContentEditor:
    def __init__(self, parent, project, on_close):
        self.project, self.on_close = project, on_close
        _, phrases, rules, self.original = project.load_editor()
        self.initial_phrases, self.initial_rules = phrases, rules
        self.window = tk.Toplevel(parent)
        self.window.title("Frases y fechas · Ink Calendar")
        self.window.geometry("820x620")
        self.window.minsize(620, 480)
        self.window.protocol("WM_DELETE_WINDOW", self.close)
        box = ttk.Frame(self.window, padding=14)
        box.pack(fill="both", expand=True)
        ttk.Label(box, text="Guarda aquí y después usa Compilar y cargar por USB.").pack(anchor="w", pady=(0, 8))
        tabs = ttk.Notebook(box)
        tabs.pack(fill="both", expand=True)
        normal, special = ttk.Frame(tabs, padding=8), ttk.Frame(tabs, padding=8)
        tabs.add(normal, text="Frases normales")
        tabs.add(special, text="Fechas y reglas (JSON)")
        ttk.Label(normal, text="Una frase por línea. Se recorren en este orden y vuelven al principio.\nLas líneas vacías se omiten; máximo 256 frases y 160 caracteres visibles por frase.").pack(anchor="w", pady=(0, 8))
        self.phrases = scrolledtext.ScrolledText(normal, font=("Segoe UI", 11), wrap="word")
        self.phrases.pack(fill="both", expand=True)
        self.phrases.insert("1.0", phrases)
        ttk.Label(special, text='Fechas MM-DD: 02-14 = 14 de febrero. specialPhrases: {"date":"02-14","phrase":"Texto"}.\nspecialImages: {"date":"02-14","image":"foto.epd"}. Se repiten cada año; cada excepción pausa su lista.',
                  wraplength=740).pack(anchor="w", pady=(0, 8))
        self.rules = scrolledtext.ScrolledText(special, font=("Consolas", 10), wrap="none")
        self.rules.pack(fill="both", expand=True)
        self.rules.insert("1.0", rules)
        ttk.Button(box, text="Guardar y cerrar", command=self.save).pack(anchor="e", pady=(10, 0))

    def save(self):
        try:
            self.project.save_editor(self.phrases.get("1.0", "end-1c"), self.rules.get("1.0", "end-1c"), self.original)
        except (OSError, ValueError, KeyError) as error:
            messagebox.showerror("No se guardaron todos los cambios", str(error), parent=self.window)
            return False
        self.window.destroy()
        self.on_close()
        return True

    def close(self):
        dirty = self.phrases.get("1.0", "end-1c") != self.initial_phrases or self.rules.get("1.0", "end-1c") != self.initial_rules
        if dirty:
            answer = messagebox.askyesnocancel("Cambios pendientes", "¿Guardar antes de cerrar?", parent=self.window)
            if answer is None:
                return
            if answer:
                self.save()
                return
        self.window.destroy()
        self.on_close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--demo", action="store_true", help="Preseleccionar simulador, sin abrir puertos físicos")
    args = parser.parse_args()
    root = tk.Tk()
    UsbCalendarApp(root, demo=args.demo)
    root.mainloop()


if __name__ == "__main__":
    main()
