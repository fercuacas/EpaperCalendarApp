package com.fernando.epapercalendar.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fernando.epapercalendar.calendar.CalendarState
import com.fernando.epapercalendar.calendar.device.DeviceCalendarController
import com.fernando.epapercalendar.epaper.EpaperHttpClient
import com.fernando.epapercalendar.epaper.EpaperNsdDiscovery
import com.fernando.epapercalendar.personal.PersonalContentCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EpaperSetupDialog(
    state: CalendarState,
    deviceCalendars: DeviceCalendarController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalCalendarPalette.current
    val preferences = remember { context.applicationContext.getSharedPreferences("epaper_calendar", 0) }
    val client = remember { EpaperHttpClient() }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(preferences.getString("epaper_host", "") ?: "") }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Busca el dispositivo o usa 192.168.4.1 durante la configuración inicial.") }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val discovery = remember {
        EpaperNsdDiscovery(
            context = context.applicationContext,
            onFound = { found ->
                host = found
                preferences.edit().putString("epaper_host", found).apply()
            },
            onStatus = { status = it }
        )
    }

    DisposableEffect(discovery) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    fun rememberHost() {
        preferences.edit().putString("epaper_host", host.trim()).apply()
    }

    suspend fun contentCompatibility(response: String): String = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(PersonalContentCatalog.ASSET_PATH).bufferedReader().use {
                PersonalContentCatalog.fromJson(it.readText()).deviceCompatibility(response)
            }
        }.getOrElse { "No se pudo verificar la coincidencia de las fotos." }
    }

    fun runNetwork(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                status = block()
                state.updateSyncStatus(status)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "Error de conexión desconocido."
                state.updateSyncStatus(status)
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(8.dp),
        containerColor = palette.surface,
        title = { Text("Configurar e-paper", color = palette.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 590.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(status, color = palette.textMuted, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Host o IP") },
                    placeholder = { Text("inkcalendar.local o 192.168.4.1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            status = "Buscando inkcalendar en la red local…"
                            discovery.start()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Text(" Buscar")
                    }
                    OutlinedButton(
                        onClick = {
                            host = "192.168.4.1"
                            rememberHost()
                            status = "Usando el punto de acceso del XIAO."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null)
                        Text(" Usar AP")
                    }
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Text(" Abrir ajustes Wi-Fi")
                }

                Text("Red doméstica", color = palette.text, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Conecta antes el móvil a InkCalendar32 (clave: inkcalendar). La contraseña de casa se envía al ESP32 y no se guarda en el móvil.",
                    color = palette.textMuted
                )
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("SSID de casa") }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    enabled = !busy && host.isNotBlank() && ssid.isNotBlank(),
                    onClick = {
                        rememberHost()
                        status = "Enviando credenciales al ESP32…"
                        runNetwork {
                            withContext(Dispatchers.IO) { client.configureWifi(host, ssid, password) }
                            password = ""
                            "Credenciales recibidas. El AP se desconectará; vuelve a la red de casa y busca el dispositivo."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text("Configurar Wi-Fi del ESP32")
                }

                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            status = "Actualizando calendarios y preparando JSON…"
                            runNetwork {
                                preview = deviceCalendars.prepareEpaperPayload(state).json
                                "Previsualización preparada con los calendarios actualizados."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ver JSON")
                    }
                    OutlinedButton(
                        enabled = !busy && host.isNotBlank(),
                        onClick = {
                            rememberHost()
                            status = "Consultando el estado del dispositivo…"
                            runNetwork {
                                val response = withContext(Dispatchers.IO) { client.getStatus(host) }
                                "Conectado: HTTP ${response.statusCode}. ${contentCompatibility(response.body)}"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Probar")
                    }
                }
                Button(
                    enabled = !busy && host.isNotBlank(),
                    onClick = {
                        rememberHost()
                        status = "Generando y enviando el calendario…"
                        runNetwork {
                            val payload = deviceCalendars.prepareEpaperPayload(state)
                            val deviceStatus = withContext(Dispatchers.IO) { client.getStatus(host) }
                            val contentStatus = contentCompatibility(deviceStatus.body)
                            val response = withContext(Dispatchers.IO) { client.sendCalendar(host, payload.json) }
                            "Sincronización aceptada (${payload.occurrenceCount} apariciones, HTTP ${response.statusCode}). $contentStatus Las fotos se actualizan al cargar el firmware, no con este envío."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null)
                    Text(if (busy) " Enviando…" else " Sincronizar ahora")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cerrar", color = palette.accent, fontWeight = FontWeight.Bold)
            }
        }
    )

    preview?.let { payload ->
        PayloadPreviewDialog(payload = payload, onDismiss = { preview = null })
    }
}
