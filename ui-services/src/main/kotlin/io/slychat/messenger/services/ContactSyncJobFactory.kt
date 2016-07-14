package io.slychat.messenger.services

interface ContactSyncJobFactory {
    fun create(): ContactSyncJob
}