package io.slychat.messenger.core.http.api.versioncheck

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.typeRef

class ClientVersionClientImpl(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    /** Returns true if the client version is up to date, false otherwise. */
    fun check(version: String): Boolean {
        val url = "$serverBaseUrl/v1/client-version/check?v=$version"

        return apiGetRequest(httpClient, url, null, emptyList(), typeRef())
    }
}