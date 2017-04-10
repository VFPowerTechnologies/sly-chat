package io.slychat.messenger.services.files

class TruncatedFileException(val received: Long, val expected: Long) : RuntimeException("Received $received but expected $expected")