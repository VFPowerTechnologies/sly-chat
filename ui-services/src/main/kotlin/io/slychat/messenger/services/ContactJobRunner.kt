package io.slychat.messenger.services

import nl.komponents.kovenant.Promise
import rx.Observable

/** Manages a sync job. */
interface ContactJobRunner {
    /** Fires once with isRunning = true on job start, isRunning = false on job completion (regardless of outcome). */
    val running: Observable<ContactJobInfo>

    fun withCurrentJob(body: ContactJobDescription.() -> Unit)

    fun shutdown()

    fun runOperation(operation: () -> Promise<*, Exception>)
}