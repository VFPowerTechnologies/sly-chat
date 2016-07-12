package io.slychat.messenger.services

import nl.komponents.kovenant.Promise

interface ContactJob {
    fun run(jobDescription: ContactJobDescription): Promise<Unit, Exception>
}