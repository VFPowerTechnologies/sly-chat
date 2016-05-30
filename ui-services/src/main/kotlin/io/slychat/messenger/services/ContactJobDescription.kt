package io.slychat.messenger.services

/** A description encapsulating a contact job and its dependencies on other contact jobs. */
class ContactJobDescription {
    var unadded: Boolean = false
        private set

    var updateRemote: Boolean = false
        private set

    var localSync: Boolean = false
        private set

    var remoteSync: Boolean = false
        private set

    fun doLocalSync(): ContactJobDescription {
        unadded = true
        localSync = true
        updateRemote = true
        return this
    }

    fun doRemoteSync(): ContactJobDescription {
        unadded = true
        localSync = true
        updateRemote = true
        remoteSync = true
        return this
    }

    fun doProcessUnaddedContacts(): ContactJobDescription {
        unadded = true
        updateRemote = true
        return this
    }

    fun doUpdateRemoteContactList(): ContactJobDescription {
        //while not strictly necessary, might as well
        unadded = true
        updateRemote = true
        return this
    }
}