package io.slychat.messenger.services

/** A description encapsulating a contact sync job and its dependencies on other contact jobs. */
class ContactJobDescription {
    var updateRemote: Boolean = false
        private set

    var localSync: Boolean = false
        private set

    var remoteSync: Boolean = false
        private set

    fun doLocalSync(): ContactJobDescription {
        localSync = true
        updateRemote = true
        return this
    }

    fun doRemoteSync(): ContactJobDescription {
        localSync = true
        updateRemote = true
        remoteSync = true
        return this
    }

    fun doUpdateRemoteContactList(): ContactJobDescription {
        //while not strictly necessary, might as well
        updateRemote = true
        return this
    }
}