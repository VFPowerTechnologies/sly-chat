package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.http.api.ServerErrorException
import com.vfpowertech.keytap.core.http.api.UnexpectedResponseException

/**
 * @param serverBaseUrl protocol://hostname[:port] with no trailing slash
 */
class RegistrationClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun register(request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/register"

        val objectMapper = ObjectMapper()
        val jsonRequest = objectMapper.writeValueAsBytes(request)

        val resp = httpClient.postJSON(url, jsonRequest)
        return when (resp.responseCode) {
            200, 400 -> try {
                objectMapper.readValue(resp.data, RegisterResponse::class.java)
            }
            catch (e: JsonProcessingException) {
                throw InvalidResponseBodyException(resp, e)
            }
            in 500..599 -> throw ServerErrorException(resp)
            else -> throw UnexpectedResponseException(resp)
        }
    }
}
