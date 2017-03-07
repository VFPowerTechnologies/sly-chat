package io.slychat.messenger.services.files

class FileMissingException(val fileId: String) : RuntimeException("File $fileId not found on server")