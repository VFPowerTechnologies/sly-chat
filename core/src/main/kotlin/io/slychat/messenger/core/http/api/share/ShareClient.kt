package io.slychat.messenger.core.http.api.share

import io.slychat.messenger.core.UserCredentials

interface ShareClient {
    fun acceptShare(userCredentials: UserCredentials, request: AcceptShareRequest): AcceptShareResponse
}