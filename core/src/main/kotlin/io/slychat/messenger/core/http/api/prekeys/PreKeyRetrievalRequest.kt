package io.slychat.messenger.core.http.api.prekeys

import io.slychat.messenger.core.UserId

/**
 * @property deviceIds If empty, fetches for all available devices.
 */
data class PreKeyRetrievalRequest(
    val userId: UserId,
    val deviceIds: List<Int>
)