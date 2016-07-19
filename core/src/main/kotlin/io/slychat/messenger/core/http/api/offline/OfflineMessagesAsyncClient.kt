package io.slychat.messenger.core.http.api.offline

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface OfflineMessagesAsyncClient {
    fun get(userCredentials: UserCredentials): Promise<OfflineMessagesGetResponse, Exception>

    fun clear(userCredentials: UserCredentials, request: OfflineMessagesClearRequest): Promise<Unit, Exception>
}
