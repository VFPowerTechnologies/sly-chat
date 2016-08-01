package io.slychat.messenger.services.contacts

/** A description encapsulating a contact sync job and its dependencies on other contact jobs. */
class AddressBookSyncJobDescription {
    var updateRemote: Boolean = false
        private set

    var platformContactSync: Boolean = false
        private set

    var remoteSync: Boolean = false
        private set

    fun doPlatformContactSync(): AddressBookSyncJobDescription {
        platformContactSync = true
        updateRemote = true
        return this
    }

    fun doRemoteSync(): AddressBookSyncJobDescription {
        platformContactSync = true
        updateRemote = true
        remoteSync = true
        return this
    }

    fun doUpdateRemoteContactList(): AddressBookSyncJobDescription {
        //while not strictly necessary, might as well
        updateRemote = true
        return this
    }
}