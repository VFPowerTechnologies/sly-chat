package io.slychat.messenger.services.messaging

interface MessageExpirationWatcher {
    fun init()
    fun shutdown()
}
