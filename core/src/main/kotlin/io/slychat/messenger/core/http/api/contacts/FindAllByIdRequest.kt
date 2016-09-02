package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserId

data class FindAllByIdRequest(
    val ids: List<UserId>
)