package io.slychat.messenger.core.persistence

class InvalidUploadException(val uploadId: String) : RuntimeException("Upload $uploadId doesn't exist")