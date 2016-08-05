package io.slychat.messenger.core

class UnsupportedOsException(val os: Os) : UnsupportedOperationException("Unsupported OS: ${os.name}")