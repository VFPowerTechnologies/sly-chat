package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

open class ApiException(message: String, val response: HttpResponse, cause: Throwable?) :
    RuntimeException(message, cause) {
    constructor(message: String, response: HttpResponse) : this(message, response, null)
}
