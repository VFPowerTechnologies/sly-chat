package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

/** Indicates that the server replied with an invalid response body for the received response code. */
class InvalidResponseBodyException(val response: HttpResponse, cause: Throwable?) :
    RuntimeException("Invalid response from server for ${response.code}", cause) {
    constructor(response: HttpResponse) : this(response, null)
}
