package io.slychat.messenger.core.persistence

class InvalidUploadPartException(val uploadId: String, val n: Int) : RuntimeException("Part $n for upload $uploadId doesn't exist")