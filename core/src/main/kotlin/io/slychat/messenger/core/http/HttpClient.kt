package io.slychat.messenger.core.http

/** Simple HTTP Client. */
interface HttpClient {
    /** Get a http resource. */
    fun get(url: String, headers: List<Pair<String, String>>): HttpResponse

    /** Post the given data as application/json. */
    fun postJSON(url: String, body: ByteArray, headers: List<Pair<String, String>>): HttpResponse
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
