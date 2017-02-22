package io.slychat.messenger.core.http

import io.slychat.messenger.core.crypto.tls.SSLConfigurator
import java.io.*
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import javax.net.ssl.HttpsURLConnection


fun slurpInputStreamReader(reader: Reader, suggestedBufferSize: Int = 0): String {
    val bufferSize = if (suggestedBufferSize > 0) suggestedBufferSize else 1024

    val buffer = CharArray(bufferSize)
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
    headers.mapKeys { e ->
        @Suppress("UNNECESSARY_SAFE_CALL")
        e.key?.toLowerCase()
    }

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

class JavaHttpClient(
    private val config: HttpClientConfig = HttpClientConfig(3000, 3000),
    private val sslConfigurator: SSLConfigurator? = null
) : HttpClient {
    private fun getHttpConnection(url: String): HttpURLConnection = getHttpConnection(URL(url))

    private fun getHttpConnection(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection)
            sslConfigurator?.configure(connection)

        return connection
    }

    override fun get(url: String, headers: List<Pair<String, String>>): HttpResponse {
        val connection = getHttpConnection(url)
        connection.doInput = true
        connection.requestMethod = "GET"
        connection.useCaches = false

        connection.connectTimeout = config.connectTimeoutMs
        connection.readTimeout = config.readTimeoutMs

        for (header in headers)
            connection.setRequestProperty(header.first, header.second)

        connection.connect()

        val headers = lowercaseHeaders(connection.headerFields ?: mapOf())
        val data = readStreamResponse(connection, headers)

        return HttpResponse(connection.responseCode, headers, data)
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

        val headers = lowercaseHeaders(connection.headerFields ?: mapOf())
        val data = readStreamResponse(connection, headers)

        return HttpResponse(connection.responseCode, headers, data)
    }

    //TODO write multipart support for builtin HttpServer to test this
    override fun upload(url: String, headers: List<Pair<String, String>>, entities: List<MultipartEntity>, filterStream: ((OutputStream) -> FilterOutputStream)?): HttpResponse {
        val connection = getHttpConnection(url)

        connection.doInput = true
        connection.doOutput = true
        connection.requestMethod = "POST"
        connection.useCaches = false

        for (header in headers)
            connection.setRequestProperty(header.first, header.second)

        val boundary = generateBoundary()

        val contentLength = calcMultipartTotalSize(boundary, entities)

        connection.setFixedLengthStreamingMode(contentLength)

        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        //HttpURLConnection will wait for 5s for a 100, and then send data; this is to support servers that don't
        //understand the Expect header; there's no way to configure this timeout
        connection.setRequestProperty("Expect", "100-Continue")

        val rawOutputStream = try {
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

        val outputStream = if (filterStream != null)
            filterStream(rawOutputStream)
        else
            rawOutputStream

        outputStream.buffered().use {
            writeMultipartEntities(it, boundary, entities)
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
