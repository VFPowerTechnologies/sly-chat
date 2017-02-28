@file:JvmName("ApiUtils")
package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UnauthorizedException
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.HttpResponse
import io.slychat.messenger.core.http.get
import org.spongycastle.util.encoders.Base64

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

internal fun <T> throwApiException(response: HttpResponse, typeReference: TypeReference<ApiResult<T>>): Nothing {
   when (response.code) {
        401 ->
            throw UnauthorizedException()
        409 ->
            throw ResourceConflictException()
        429 ->
            throw TooManyRequestsException()
        in 500..599 -> {
            val apiValue = readValueOrThrowInvalid(response, typeReference)
            throw ServerErrorException(response, apiValue.error)
        }
        else -> throw UnexpectedResponseException(response)
    }
}

/** Throws an ApiResultException if an api-level error occured, otherwise returns the request value. */
fun <T> valueFromApi(response: HttpResponse, typeReference: TypeReference<ApiResult<T>>): T {
    val apiResult = if (response.code == 200 || response.code == 400)
        readValueOrThrowInvalid(response, typeReference)
    else
        throwApiException(response, typeReference)

    return getValueFromApiResult(apiResult, response)
}

internal fun userCredentialsToHeaders(userCredentials: UserCredentials?): List<Pair<String, String>> {
    return if (userCredentials != null) {
        val username = userCredentials.address.asString()
        val creds = "$username:${userCredentials.authToken.string}".toByteArray(Charsets.UTF_8)
        val encoded = Base64.encode(creds).toString(Charsets.US_ASCII)
        listOf("Authorization" to "Basic $encoded")
    }
    else
        listOf()
}

fun <R, T> apiPostRequest(httpClient: HttpClient, url: String, userCredentials: UserCredentials?, request: R?, typeReference: TypeReference<ApiResult<T>>): T {
    val objectMapper = ObjectMapper()
    val headers = userCredentialsToHeaders(userCredentials)

    val jsonRequest = if (request != null)
        objectMapper.writeValueAsBytes(request)
    else
        "{}".toByteArray()

    val resp = httpClient.postJSON(url, jsonRequest, headers)
    return valueFromApi(resp, typeReference)
}

fun <T> apiGetRequest(httpClient: HttpClient, url: String, userCredentials: UserCredentials?, params: List<Pair<String, String>>, typeReference: TypeReference<ApiResult<T>>): T {
    val headers = userCredentialsToHeaders(userCredentials)

    val resp = httpClient.get(url, params, headers)
    return valueFromApi(resp, typeReference)
}
