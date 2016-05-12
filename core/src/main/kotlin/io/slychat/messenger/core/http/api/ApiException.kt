package io.slychat.messenger.core.http.api

import io.slychat.messenger.core.http.HttpResponse

open class ApiException(message: String, val response: HttpResponse, cause: Throwable?) :
    RuntimeException(message, cause) {
    constructor(message: String, response: HttpResponse) : this(message, response, null)
}
