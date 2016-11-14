package io.slychat.messenger.core.http.api.versioncheck

interface ClientVersionClient {
    fun check(version: String): CheckResponse
}