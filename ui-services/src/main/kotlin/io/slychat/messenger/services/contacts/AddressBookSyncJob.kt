package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise

interface AddressBookSyncJob {
    fun run(jobDescription: AddressBookSyncJobDescription): Promise<AddressBookSyncResult, Exception>
}