package io.slychat.messenger.services.contacts

/** A description encapsulating a contact sync job and its dependencies on other contact jobs. */
class AddressBookSyncJobDescription {
    var push: Boolean = false
        private set

    var findPlatformContacts: Boolean = false
        private set

    var pull: Boolean = false
        private set

    fun doFindPlatformContacts(): AddressBookSyncJobDescription {
        findPlatformContacts = true
        push = true
        return this
    }

    fun doPull(): AddressBookSyncJobDescription {
        findPlatformContacts = true
        push = true
        pull = true
        return this
    }

    fun doPush(): AddressBookSyncJobDescription {
        //while not strictly necessary, might as well
        push = true
        return this
    }
}