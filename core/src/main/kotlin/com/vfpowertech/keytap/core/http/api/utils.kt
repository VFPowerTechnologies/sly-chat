@file:JvmName("ApiUtils")
package com.vfpowertech.keytap.core.http.api

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.http.get

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

/** Posts the given request to the given url as JSON, then passes the response to valueFromApi. */
fun <R, T> apiPostRequest(httpClient: HttpClient, url: String, request: R, validResponseCodes: Set<Int>, typeReference: TypeReference<ApiResult<T>>): T {
    val objectMapper = ObjectMapper()
    val jsonRequest = objectMapper.writeValueAsBytes(request)

    val resp = httpClient.postJSON(url, jsonRequest)
    return valueFromApi(resp, validResponseCodes, typeReference)
}

fun <T> apiGetRequest(httpClient: HttpClient, url: String, params: List<Pair<String, String>>, validResponseCodes: Set<Int>, typeReference: TypeReference<ApiResult<T>>): T {
    val resp = httpClient.get(url, params)
    return valueFromApi(resp, validResponseCodes, typeReference)
}
