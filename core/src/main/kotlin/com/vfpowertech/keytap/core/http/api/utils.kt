@file:JvmName("ApiUtils")
package com.vfpowertech.keytap.core.http.api

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.UnauthorizedException
import com.vfpowertech.keytap.core.base64encode
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.http.get
import com.vfpowertech.keytap.core.relay.UserCredentials

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
            throw UnauthorizedException()
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

private fun userCredentialsToHeaders(userCredentials: UserCredentials?): List<Pair<String, String>> {
    return if (userCredentials != null) {
        //RFC2617 doesn't allow colons in username, as it's used to delimit the username and password fields
        val username = userCredentials.address.asString().replace(':', '.')
        val creds = "$username:${userCredentials.authToken}".toByteArray(Charsets.UTF_8)
        listOf("Authorization" to "Basic ${base64encode(creds)}")
    }
    else
        listOf()
}

/** Posts the given request to the given url as JSON, then passes the response to valueFromApi. */
fun <R, T> apiPostRequest(httpClient: HttpClient, url: String, request: R, validResponseCodes: Set<Int>, typeReference: TypeReference<ApiResult<T>>): T {
    val objectMapper = ObjectMapper()
    val jsonRequest = objectMapper.writeValueAsBytes(request)

    val resp = httpClient.postJSON(url, jsonRequest, listOf())
    return valueFromApi(resp, validResponseCodes, typeReference)
}
fun <R, T> apiPostRequest2(httpClient: HttpClient, url: String, userCredentials: UserCredentials?, request: R, validResponseCodes: Set<Int>, typeReference: TypeReference<ApiResult<T>>): T {
    val objectMapper = ObjectMapper()
    val jsonRequest = objectMapper.writeValueAsBytes(request)

    val headers = userCredentialsToHeaders(userCredentials)

    val resp = httpClient.postJSON(url, jsonRequest, headers)
    return valueFromApi(resp, validResponseCodes, typeReference)
}

fun <T> apiGetRequest(httpClient: HttpClient, url: String, params: List<Pair<String, String>>, validResponseCodes: Set<Int>, typeReference: TypeReference<ApiResult<T>>): T {
    val resp = httpClient.get(url, params, listOf())
    return valueFromApi(resp, validResponseCodes, typeReference)
}

fun <T> apiGetRequest2(httpClient: HttpClient, url: String, userCredentials: UserCredentials?, params: List<Pair<String, String>>, validResponseCodes: Set<Int>, typeReference: TypeReference<ApiResult<T>>): T {
    val headers = userCredentialsToHeaders(userCredentials)

    val resp = httpClient.get(url, params, headers)
    return valueFromApi(resp, validResponseCodes, typeReference)
}
