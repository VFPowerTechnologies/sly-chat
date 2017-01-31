package io.slychat.messenger.core.sentry

import nl.komponents.kovenant.Promise

interface ReportSubmitter<in ReportType> {
    /** Will be resolved once the ReportSubmitterImpl has finished shutting down. Will never be rejected. */
    val shutdownPromise: Promise<Unit, Exception>

    fun shutdown()

    fun updateNetworkStatus(isAvailable: Boolean)

    fun submit(report: ReportType)

    fun run()
}