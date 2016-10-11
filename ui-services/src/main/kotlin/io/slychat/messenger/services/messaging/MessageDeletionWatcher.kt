package io.slychat.messenger.services.messaging

interface MessageDeletionWatcher {
    fun init()
    fun shutdown()
}