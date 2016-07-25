package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise

interface ContactSyncJob {
    fun run(jobDescription: ContactSyncJobDescription): Promise<Unit, Exception>
}