package io.slychat.messenger.services.contacts

interface ContactSyncJobFactory {
    fun create(): ContactSyncJob
}