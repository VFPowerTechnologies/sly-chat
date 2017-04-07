package io.slychat.messenger.core.http.api.share

import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise

interface ShareAsyncClient {
    fun acceptShare(userCredentials: UserCredentials, request: AcceptShareRequest): Promise<AcceptShareResponse, Exception>
}