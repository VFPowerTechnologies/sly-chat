package com.vfpowertech.keytap.core.http

/**
 * Response from a HTTP server.
 *
 * @property responseCode HTTP response code.
 * @property headers Header names will always be in lowercase.
 * @property data Server response data.
 *
 * @constructor
 */
data class HttpResponse(
    val responseCode: Int,
    val headers: Map<String, List<String>>,
    val data: String
)
