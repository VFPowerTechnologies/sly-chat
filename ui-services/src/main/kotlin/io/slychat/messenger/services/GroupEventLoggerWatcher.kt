package io.slychat.messenger.services

interface GroupEventLoggerWatcher {
    fun init()
    fun shutdown()
}