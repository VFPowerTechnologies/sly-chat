package io.slychat.messenger.core.sentry

import nl.komponents.kovenant.Promise

/** Communicate with a running ReportSubmitter. */
interface ReportSubmitterCommunicator<in ReportType> {
    /** Will be resolved once the ReportSubmitter has finished shutting down. Will never be rejected. */
    val shutdownPromise: Promise<Unit, Exception>

    fun shutdown()

    fun updateNetworkStatus(isAvailable: Boolean)

    fun submit(report: ReportType)

    fun run()
}