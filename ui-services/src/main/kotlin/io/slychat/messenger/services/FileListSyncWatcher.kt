package io.slychat.messenger.services

interface FileListSyncWatcher {
    fun init()

    fun shutdown()
}