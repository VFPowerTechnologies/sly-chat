@file:JvmName("SentryUtils")
package io.slychat.messenger.core.sentry

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.http.HttpClientFactory
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

fun SentryEvent.serialize(): ByteArray {
    val objectMapper = ObjectMapper()
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

    val body = objectMapper.writeValueAsBytes(this)

    val byteStream = ByteArrayOutputStream()
    DeflaterOutputStream(byteStream).use { os ->
        os.write(body)
    }

    return byteStream.toByteArray()
}

fun postEvent(dsn: DSN, httpClientFactory: HttpClientFactory, bytes: ByteArray) {
    val url = dsn.getStoreUrl()

    val timestamp = System.currentTimeMillis().toString()

    val publicKey = dsn.publicKey
    val privateKey = dsn.privateKey

    val authHeader = "Sentry sentry_version=7, sentry_timestamp=$timestamp, sentry_key=$publicKey, sentry_secret=$privateKey"

    val headers = listOf(
        "Content-Encoding" to "deflate",
        "User-Agent" to "Sly-Raven/${SlyBuildConfig.VERSION}",
        "X-Sentry-Auth" to authHeader
    )

    val client = httpClientFactory.create()

    //400 for malformed data
    //401 if key is invalid
    val response = client.post(url, bytes, headers)

    if (!response.isSuccess) {
        val error = response.headers["x-sentry-error"]?.get(0) ?: "No error message"
        //TODO improve this
        val isRecoverable = response.code != 400 && response.code != 401

        throw SentryException(error, isRecoverable)
    }
}
