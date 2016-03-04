@file:JvmName("ApiUtils")
package com.vfpowertech.keytap.core.http.api

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
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

fun <T> getValueFromApiResult(apiResult: ApiResult<T>, response: HttpResponse): T {
    //should never happen as ApiResult has checks in its constructor for this stuff
    if (!apiResult.isError)
        return apiResult.value ?: throw IllegalArgumentException("ApiResult with both error and value set to null")

    val error = apiResult.error!!

    throw ApiException(error.message, response)
}

private fun <T> readValueOrThrowInvalid(response: HttpResponse, typeReference: TypeReference<ApiResult<T>>): ApiResult<T> {
    return try {
        ObjectMapper().readValue<ApiResult<T>>(response.body, typeReference)
    }
    catch (e: JsonProcessingException) {
        throw InvalidResponseBodyException(response, e)
    }
}

/** Throws an ApiResultException if an api-level error occured, otherwise returns the request value. */
fun <T> valueFromApi(response: HttpResponse, validResponseCodes: Set<Int>, typeReference: TypeReference<ApiResult<T>>): T {
    val apiResult = when (response.code) {
        401 ->
            throw UnauthorizedException(response)
        in validResponseCodes ->
            readValueOrThrowInvalid(response, typeReference)
        in 500..599 -> {
            val apiValue = readValueOrThrowInvalid(response, typeReference)
            throw ServerErrorException(response, apiValue.error)
        }
        else -> throw UnexpectedResponseException(response)
    }

    return getValueFromApiResult(apiResult, response)
}
