package com.fernando.epapercalendar.epaper

import java.net.HttpURLConnection
import java.net.URL

data class EpaperHttpResponse(val statusCode: Int, val body: String)

class EpaperHttpException(val statusCode: Int, responseBody: String) :
    Exception("El dispositivo respondió HTTP $statusCode: ${responseBody.take(240)}")

class EpaperHttpClient(
    private val connectTimeoutMillis: Int = 8_000,
    private val readTimeoutMillis: Int = 20_000
) {
    fun getStatus(host: String): EpaperHttpResponse = request(host, "/status", "GET", null)

    fun configureWifi(host: String, ssid: String, password: String): EpaperHttpResponse {
        val body = "{\"ssid\":\"${escapeJson(ssid)}\",\"password\":\"${escapeJson(password)}\"}"
        return request(host, "/config/wifi", "POST", body)
    }

    fun sendCalendar(host: String, payload: String): EpaperHttpResponse =
        request(host, "/calendar", "POST", payload)

    private fun request(host: String, path: String, method: String, body: String?): EpaperHttpResponse {
        val base = normalizeHost(host)
        val connection = URL("$base$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw EpaperHttpException(status, responseBody)
            EpaperHttpResponse(status, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun normalizeHost(host: String): String {
            val trimmed = host.trim().trimEnd('/')
            require(trimmed.isNotEmpty()) { "Indica el host o la IP del dispositivo." }
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        }
    }
}
