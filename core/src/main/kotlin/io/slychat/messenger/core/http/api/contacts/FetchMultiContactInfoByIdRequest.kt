package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserId

data class FetchMultiContactInfoByIdRequest(
    val ids: List<UserId>
)