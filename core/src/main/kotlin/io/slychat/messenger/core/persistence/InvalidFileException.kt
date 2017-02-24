package io.slychat.messenger.core.persistence

class InvalidFileException(val fileId: String) : RuntimeException("File $fileId doesn't exist")