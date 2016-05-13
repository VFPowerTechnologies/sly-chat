package io.slychat.messenger.core.http.api

import io.slychat.messenger.core.http.HttpResponse

/** Indicates that the server replied with an invalid response body for the received response code. */
class InvalidResponseBodyException(response: HttpResponse, cause: Throwable?) :
    ApiException("Invalid response from server for ${response.code}", response, cause) {
    constructor(response: HttpResponse) : this(response, null)
}
