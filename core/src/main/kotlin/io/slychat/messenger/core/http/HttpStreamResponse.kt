package io.slychat.messenger.core.http

import java.io.InputStream

/**
 * @property body Must be closed by the caller.
 */
class HttpStreamResponse(
    val code: Int,
    val headers: Map<String, List<String>>,
    val body: InputStream
) {
    val isSuccess: Boolean
        get() = code >= 200 && code < 300
}