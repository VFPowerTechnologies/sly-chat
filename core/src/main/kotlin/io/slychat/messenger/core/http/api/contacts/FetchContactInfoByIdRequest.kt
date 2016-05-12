package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId

data class FetchContactInfoByIdRequest(
    val ids: List<UserId>
)