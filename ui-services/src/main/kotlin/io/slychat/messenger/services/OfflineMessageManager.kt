package io.slychat.messenger.services

interface OfflineMessageManager {
    fun fetch()
    fun shutdown()
}