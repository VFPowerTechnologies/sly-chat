package io.slychat.messenger.services

interface PreKeyManager {
    fun checkForUpload()
    fun scheduleUpload(keyRegenCount: Int)
    fun shutdown()
}