@file:JvmName("HttpUtils")
package io.slychat.messenger.core.http

import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.*

fun toQueryString(params: List<Pair<String, String>>): String {
    if (params.isEmpty())
        return ""

    val builder = StringBuilder()

    for ((k, v) in params) {
        builder.append(URLEncoder.encode(k, "UTF-8"))
        builder.append("=")
        builder.append(URLEncoder.encode(v, "UTF-8"))
        builder.append("&")
    }
    builder.deleteCharAt(builder.length-1)

    return builder.toString()
}

//this is what apache's httpclient does
private val MULTIPART_CHARS = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
fun generateBoundary(): String {
    val builder = StringBuilder()
    val random = Random()
    val count = random.nextInt(10) + 30

    for (i in 0..count)
        builder.append(MULTIPART_CHARS[random.nextInt(MULTIPART_CHARS.length)])

    return builder.toString()
}

sealed class MultipartEntity {
    abstract val name: String
    abstract val contentType: String?
    abstract val size: Long

    class Text(override val name: String, val value: String) : MultipartEntity() {
        private val raw = value.toByteArray(Charsets.US_ASCII)

        override val size: Long
            get() = raw.size.toLong()

        //default Content-Type is text/plain; we're not sending utf8 so omit it
        override val contentType: String?
            get() = null
    }

    class Data(override val name: String, override val size: Long, val inputStream: InputStream) : MultipartEntity() {
        override val contentType: String?
            get() = "application/octet-stream"
    }
}

fun calcMultipartEntitySize(entity: MultipartEntity, boundary: String): Long {
    var totalSize = 0L

    //-- + boundary + \r\n
    totalSize += boundary.length + 4

    totalSize += "Content-Disposition: form-data; name=\"${entity.name}\"\r\n".toByteArray(Charsets.US_ASCII).size

    if (entity.contentType != null)
        totalSize += "Content-Type: ${entity.contentType}\r\n".toByteArray(Charsets.US_ASCII).size

    //\r\n
    totalSize += 2

    //value size
    totalSize += entity.size

    //\r\n after value
    totalSize += 2

    return totalSize
}

fun calcMultipartTotalSize(boundary: String, entities: Iterable<MultipartEntity>): Long {
    //--boundary--\r\n
    var totalSize =  boundary.toByteArray(Charsets.US_ASCII).size + 6L

    entities.forEach {
        totalSize += calcMultipartEntitySize(it, boundary)
    }

    return totalSize
}

fun writeMultipartEntity(outputStream: OutputStream, boundary: String, entity: MultipartEntity) {
    outputStream.write("--$boundary\r\n".toByteArray(Charsets.US_ASCII))
    outputStream.write("Content-Disposition: form-data; name=\"${entity.name}\"\r\n".toByteArray(Charsets.US_ASCII))

    if (entity.contentType != null)
        outputStream.write("Content-Type: ${entity.contentType}\r\n".toByteArray(Charsets.US_ASCII))

    outputStream.write("\r\n".toByteArray(Charsets.US_ASCII))

    when (entity) {
        is MultipartEntity.Text -> {
            outputStream.write("${entity.value}\r\n".toByteArray(Charsets.US_ASCII))
        }

        is MultipartEntity.Data -> {
            val buffer = ByteArray(1024 * 8)

            while (true) {
                val read = entity.inputStream.read(buffer)
                if (read == -1)
                    break

                outputStream.write(buffer, 0, read)
            }

            outputStream.write("\r\n".toByteArray(Charsets.US_ASCII))
        }
    }
}

fun writeMultipartEntities(outputStream: OutputStream, boundary: String, entities: Iterable<MultipartEntity>) {
    entities.forEach {
        writeMultipartEntity(outputStream, boundary, it)
    }

    outputStream.write("--$boundary--\r\n".toByteArray(Charsets.US_ASCII))
}
