package io.slychat.messenger.core.persistence

data class UploadPart(
    val n: Int,
    val offset: Long,
    val localSize: Long,
    val remoteSize: Long,
    val isComplete: Boolean
) {
    init {
        require(n > 0) { "n must be > 0, got $n" }
        require(localSize > 0) { "localSize must be > 0, got $localSize" }
        require(remoteSize > 0) { "remoteSize must be > 0, got $remoteSize" }
        //doesn't really make sense otherwise anyways
        require(localSize <= remoteSize) { "localSize ($localSize) should be <= remoteSize ($remoteSize)" }
    }
}