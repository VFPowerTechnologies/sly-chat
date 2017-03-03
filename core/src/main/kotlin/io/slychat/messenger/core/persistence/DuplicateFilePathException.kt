package io.slychat.messenger.core.persistence

class DuplicateFilePathException(val directory: String, val fileName: String) : Exception("A file already exists at $directory/$fileName")