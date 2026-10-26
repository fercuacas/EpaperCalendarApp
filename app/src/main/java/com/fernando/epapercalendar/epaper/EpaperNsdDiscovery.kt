package com.fernando.epapercalendar.epaper

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

class EpaperNsdDiscovery(
    context: Context,
    private val onFound: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val multicastLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("epaper-calendar-nsd")
        .apply { setReferenceCounted(false) }
    private var running = false
    private var resolving = false

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            resolving = false
            onStatus("No se pudo resolver inkcalendar ($errorCode).")
        }

        @Suppress("DEPRECATION")
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            resolving = false
            val address = serviceInfo.host?.hostAddress
            if (address.isNullOrBlank()) {
                onStatus("El dispositivo no publicó una dirección utilizable.")
            } else {
                val host = if (serviceInfo.port == 80) address else "$address:${serviceInfo.port}"
                onFound(host)
                onStatus("Dispositivo encontrado en $host.")
            }
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = onStatus("Buscando inkcalendar en la red local…")
        override fun onDiscoveryStopped(serviceType: String) {
            running = false
            releaseMulticastLock()
        }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            running = false
            releaseMulticastLock()
            onStatus("No se pudo iniciar la búsqueda ($errorCode).")
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            running = false
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!serviceInfo.serviceType.contains("_inkcalendar._tcp") || resolving) return
            resolving = true
            @Suppress("DEPRECATION")
            manager.resolveService(serviceInfo, resolveListener)
        }
    }

    fun start() {
        if (running) return
        running = true
        if (!multicastLock.isHeld) multicastLock.acquire()
        manager.discoverServices("_inkcalendar._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stop() {
        if (!running) return
        runCatching { manager.stopServiceDiscovery(discoveryListener) }
        running = false
        releaseMulticastLock()
    }

    private fun releaseMulticastLock() {
        if (multicastLock.isHeld) multicastLock.release()
    }
}
