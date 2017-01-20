package io.slychat.messenger.core.sentry

/** Communicate with a running ReportSubmitter. */
interface ReportSubmitterCommunicator<in ReportType> {
    fun shutdown()

    fun updateNetworkStatus(isAvailable: Boolean)

    fun submit(report: ReportType)
}