package com.vfpowertech.keytap.core.http

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL

fun slurpInputStreamReader(reader: Reader, suggestedBufferSize: Int = 0): String {
    val bufferSize = if (suggestedBufferSize > 0) suggestedBufferSize else 1024

    val buffer = kotlin.CharArray(bufferSize)
    val builder = StringBuilder()
    while (true) {
        val readChars = reader.read(buffer, 0, buffer.size)
        if (readChars <= 0)
            break
        builder.append(buffer, 0, readChars)
    }

    return builder.toString()
}

fun lowercaseHeaders(headers: Map<String, List<String>>): Map<String, List<String>> =
    //headers actually have a null key containing the http response line
    headers.mapKeys { e -> e.key?.toLowerCase() }

fun slurpStreamAndClose(inputStream: InputStream, suggestedBufferSize: Int): String =
    inputStream.use {
        val reader = BufferedReader(InputStreamReader(it, "utf-8"))
        slurpInputStreamReader(reader, suggestedBufferSize)
    }

fun readStreamResponse(connection: HttpURLConnection, headers: Map<String, List<String>>): String {
    val contentLength = headers["content-length"]?.first()?.toInt() ?: 1024

    val data = try {
        slurpStreamAndClose(connection.inputStream, contentLength)
    }
    catch (e: java.io.IOException) {
        if (connection.errorStream != null)
            slurpStreamAndClose(connection.errorStream, contentLength)
        else
            ""
    }

    return data
}

class JavaHttpClient : HttpClient {
    override fun get(url: String): HttpResponse {
        val u = URL(url)
        val connection = u.openConnection() as HttpURLConnection
        connection.doInput = true
        connection.requestMethod = "GET"
        connection.useCaches = false

        connection.connect()

        val headers = lowercaseHeaders(connection.headerFields)
        val data = readStreamResponse(connection, headers)

        return HttpResponse(connection.responseCode, headers, data)
    }

    override fun postJSON(url: String, body: ByteArray): HttpResponse {
        val u = URL(url)
        val connection = u.openConnection() as HttpURLConnection
        connection.doInput = true
        connection.doOutput = true

        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(body.size)

        connection.connect()

        connection.outputStream.use { it.write(body) }

        val headers = lowercaseHeaders(connection.headerFields)
        val data = readStreamResponse(connection, headers)

        return HttpResponse(connection.responseCode, headers, data)
    }
}
