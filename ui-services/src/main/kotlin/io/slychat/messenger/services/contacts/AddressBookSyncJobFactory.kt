package io.slychat.messenger.services.contacts

interface AddressBookSyncJobFactory {
    fun create(): AddressBookSyncJob
}