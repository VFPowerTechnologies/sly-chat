package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonProperty

data class Quota(
    @JsonProperty("usedBytes")
    val usedBytes: Long,
    @JsonProperty("maxBytes")
    val maxBytes: Long
) {
    init {
        if (usedBytes > maxBytes)
            error("usedBytes ($usedBytes) must be <= maxBytes ($maxBytes)")
    }
}