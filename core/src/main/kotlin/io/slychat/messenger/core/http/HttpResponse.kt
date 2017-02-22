package io.slychat.messenger.core.http

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
) {
    val isSuccess: Boolean
        get() = code >= 200 && code < 300
}

