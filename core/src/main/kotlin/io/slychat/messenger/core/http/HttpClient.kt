package io.slychat.messenger.core.http

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Simple HTTP Client. */
interface HttpClient {
    /** Get a http resource. */
    fun get(url: String, headers: List<Pair<String, String>>): HttpResponse

    /** Post without a set content type. */
    fun post(url: String, body: ByteArray, headers: List<Pair<String, String>>): HttpResponse

    /** Post the given data as application/json. */
    fun postJSON(url: String, body: ByteArray, headers: List<Pair<String, String>>): HttpResponse

    fun upload(url: String, headers: List<Pair<String, String>>, parts: List<MultipartPart>, isCancelled: AtomicBoolean, filterStream: ((OutputStream) -> OutputStream)?): HttpResponse

    fun download(url: String, headers: List<Pair<String, String>>): HttpStreamResponse
}

/** GET request with query params. */
fun HttpClient.get(url: String, queryParams: List<Pair<String, String>>, headers: List<Pair<String, String>>): HttpResponse {
    val query = toQueryString(queryParams)
    val fullUrl = if (query.isNotEmpty()) "$url?$query" else url
    return get(fullUrl, headers)
}

fun HttpClient.get(url: String): HttpResponse {
   return get(url, listOf())
}

fun HttpClient.postJSON(url: String, body: ByteArray): HttpResponse {
    return postJSON(url, body, listOf())
}
