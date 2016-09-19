package io.slychat.messenger.services

interface MessageReadWatcher {
    fun init()
    fun shutdown()
}