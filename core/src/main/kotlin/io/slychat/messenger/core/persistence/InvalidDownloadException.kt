package io.slychat.messenger.core.persistence

class InvalidDownloadException(val downloadId: String) : RuntimeException("Download $downloadId doesn't exist")