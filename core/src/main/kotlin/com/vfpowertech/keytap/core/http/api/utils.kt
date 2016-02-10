@file:JvmName("ApiUtils")
package com.vfpowertech.keytap.core.http.api

import com.vfpowertech.keytap.core.http.HttpResponse

/**
 * Throws an exception when presented with an unexpected response code from an API call.
 *
 * @throws ApiException
 */
fun throwApiException(response: HttpResponse): Nothing = when (response.code) {
    401 -> throw UnauthorizedException(response)
    in 500..599 -> throw ServerErrorException(response)
    else -> throw UnexpectedResponseException(response)
}

