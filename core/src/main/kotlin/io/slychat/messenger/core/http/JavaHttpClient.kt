package io.slychat.messenger.core.http

import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import java.io.*
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

private fun lowercaseHeaders(headers: Map<String, List<String>>): Map<String, List<String>> =
    //headers actually have a null key containing the http response line
    headers.mapKeys { e ->
        @Suppress("UNNECESSARY_SAFE_CALL")
        e.key?.toLowerCase()
    }

private fun slurpStreamAndClose(inputStream: InputStream, suggestedBufferSize: Int): String =
    inputStream.use {
        val reader = BufferedReader(InputStreamReader(it, "utf-8"))
        reader.readText()
    }

private fun readStreamResponse(connection: HttpURLConnection, headers: Map<String, List<String>>): String {
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

class JavaHttpClient(
    private val config: HttpClientConfig = HttpClientConfig(3000, 3000),
    private val sslConfigurator: SSLConfigurator? = null
) : HttpClient {
    private fun getHttpConnection(url: String): HttpURLConnection = getHttpConnection(URL(url))

    private fun getHttpConnection(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection

        connection.connectTimeout = config.connectTimeoutMs
        connection.readTimeout = config.readTimeoutMs

        if (connection is HttpsURLConnection)
            sslConfigurator?.configure(connection)

        return connection
    }

    private fun readResponse(connection: HttpURLConnection): HttpResponse {
        val headers = lowercaseHeaders(connection.headerFields ?: mapOf())
        val data = readStreamResponse(connection, headers)

        return HttpResponse(connection.responseCode, headers, data)
    }

    override fun get(url: String, headers: List<Pair<String, String>>): HttpResponse {
        val connection = getHttpConnection(url)
        connection.doInput = true
        connection.requestMethod = "GET"
        connection.useCaches = false

        for (header in headers)
            connection.setRequestProperty(header.first, header.second)

        connection.connect()

        return readResponse(connection)
    }

    override fun delete(url: String, headers: List<Pair<String, String>>): HttpResponse {
        val connection = getHttpConnection(url)
        connection.doInput = true
        connection.requestMethod = "DELETE"

        for (header in headers)
            connection.setRequestProperty(header.first, header.second)

        connection.connect()

        return readResponse(connection)
    }

    override fun postJSON(url: String, body: ByteArray, headers: List<Pair<String, String>>): HttpResponse {
        val newHeaders = headers.toMutableList()
        newHeaders.add("Content-Type" to "application/json")

        return post(url, body, newHeaders)
    }

    override fun post(url: String, body: ByteArray, headers: List<Pair<String, String>>): HttpResponse {
        val connection = getHttpConnection(url)
        connection.requestMethod = "POST"
        connection.doInput = true
        connection.doOutput = true

        for (header in headers)
            connection.setRequestProperty(header.first, header.second)

        connection.setFixedLengthStreamingMode(body.size)

        connection.connect()

        connection.outputStream.use { it.write(body) }

        return readResponse(connection)
    }

    //TODO write multipart support for builtin HttpServer to test this
    override fun upload(url: String, headers: List<Pair<String, String>>, parts: List<MultipartPart>, isCancelled: AtomicBoolean): HttpResponse {
        val connection = getHttpConnection(url)

        connection.doInput = true
        connection.doOutput = true
        connection.requestMethod = "POST"
        connection.useCaches = false

        for (header in headers)
            connection.setRequestProperty(header.first, header.second)

        val boundary = generateBoundary()

        val contentLength = calcMultipartTotalSize(boundary, parts)

        connection.setFixedLengthStreamingMode(contentLength)

        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        //HttpURLConnection will wait for 5s for a 100, and then send data; this is to support servers that don't
        //understand the Expect header; there's no way to configure this timeout
        connection.setRequestProperty("Expect", "100-Continue")

        val outputStream = try {
            connection.outputStream
        }
        catch (e: ProtocolException) {
            //XXX I can't seem to get it to read the body on error; errorStream is null and inputStream throws an exception
            //also only ProtocolException is thrown here when an error occurs (see expect100Continue in the HttpURLConnection src)
            val headers = lowercaseHeaders(connection.headerFields ?: mapOf())
            val code = connection.responseCode

            val data = readStreamResponse(connection, headers)
            return HttpResponse(code, headers, data)
        }

        outputStream.buffered().use {
            writeMultipartParts(it, boundary, parts, isCancelled)
        }

        val headers = lowercaseHeaders(connection.headerFields ?: mapOf())
        val code = connection.responseCode

        val data = readStreamResponse(connection, headers)

        return HttpResponse(code, headers, data)
    }

    override fun download(url: String, headers: List<Pair<String, String>>): HttpStreamResponse {
        val connection = getHttpConnection(url)

        connection.doOutput = true
        connection.requestMethod = "GET"
        connection.useCaches = false

        for (header in headers)
            connection.setRequestProperty(header.first, header.second)

        val responseCode = connection.responseCode
        val headers = lowercaseHeaders(connection.headerFields ?: mapOf())

        val body = try {
            connection.inputStream
        }
        catch (e: IOException) {
            if (connection.errorStream != null)
                connection.errorStream
            else
                ByteArrayInputStream(ByteArray(0))
        }

        return HttpStreamResponse(responseCode, headers, body)
    }
}
