package com.vfpowertech.keytap.core.http

/** Simple HTTP Client. */
interface HttpClient {
    /** Get a http resource. */
    fun get(url: String): HttpResponse
    /** Post the given data as application/json. */
    fun postJSON(url: String, body: ByteArray): HttpResponse
}