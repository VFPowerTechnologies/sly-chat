package io.slychat.messenger.core.persistence

data class UploadPart(
    val n: Int,
    val offset: Long,
    val size: Long,
    val isComplete: Boolean
) {
    init {
        require(n > 0) { "n must be >= 0, got $n" }
    }
}