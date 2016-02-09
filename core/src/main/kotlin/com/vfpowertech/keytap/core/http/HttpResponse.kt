package com.vfpowertech.keytap.core.http

/**
 * Response from a HTTP server.
 *
 * @property code HTTP response code.
 * @property headers Header names will always be in lowercase.
 * @property body Server response body.
 *
 * @constructor
 */
data class HttpResponse(
    val code: Int,
    val headers: Map<String, List<String>>,
    val body: String
)
