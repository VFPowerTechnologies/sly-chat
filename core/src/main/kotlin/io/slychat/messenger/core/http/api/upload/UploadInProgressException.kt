package io.slychat.messenger.core.http.api.upload

class UploadInProgressException(val uploadId: String) : RuntimeException("Upload $uploadId still in progress")