@file:JvmName("ApiUtils")
package com.vfpowertech.keytap.core.http.api

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.typeRef

/**
 * Throws an exception when presented with an unexpected response code from an API call.
 *
 * @throws ApiException
 */
fun throwApiException(response: HttpResponse): Nothing = when (response.code) {
    401 -> throw UnauthorizedException(response)
    in 500..599 -> try {
        val apiValue = ObjectMapper().readValue<ApiResult<Unit>>(response.body, typeRef<ApiResult<Unit>>())
        throw ServerErrorException(response, apiValue.error)
    }
    catch (e: JsonProcessingException) {
        throw InvalidResponseBodyException(response, e)
    }
    else -> throw UnexpectedResponseException(response)
}

