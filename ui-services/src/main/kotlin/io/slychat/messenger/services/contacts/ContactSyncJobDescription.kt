package io.slychat.messenger.services.contacts

/** A description encapsulating a contact sync job and its dependencies on other contact jobs. */
class ContactSyncJobDescription {
    var updateRemote: Boolean = false
        private set

    var platformContactSync: Boolean = false
        private set

    var remoteSync: Boolean = false
        private set

    fun doPlatformContactSync(): ContactSyncJobDescription {
        platformContactSync = true
        updateRemote = true
        return this
    }

    fun doRemoteSync(): ContactSyncJobDescription {
        platformContactSync = true
        updateRemote = true
        remoteSync = true
        return this
    }

    fun doUpdateRemoteContactList(): ContactSyncJobDescription {
        //while not strictly necessary, might as well
        updateRemote = true
        return this
    }
}