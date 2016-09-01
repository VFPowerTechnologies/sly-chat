package io.slychat.messenger.core.http.api.versioncheck

import nl.komponents.kovenant.Promise

interface ClientVersionAsyncClient {
    fun check(version: String): Promise<Boolean, Exception>
}
