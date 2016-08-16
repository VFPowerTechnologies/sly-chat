package io.slychat.messenger.core.sentry

import java.util.concurrent.BlockingQueue

/** Communicate with a running ReportSubmitter. */
class ReportSubmitterCommunicator<in ReportType>(
    private val messageQueue: BlockingQueue<ReporterMessage<ReportType>>
) {
    fun shutdown() {
        messageQueue.offer(ReporterMessage.Shutdown())
    }

    fun updateNetworkStatus(isAvailable: Boolean) {
        messageQueue.offer(ReporterMessage.NetworkStatus(isAvailable))
    }

    fun submit(report: ReportType) {
        messageQueue.offer(ReporterMessage.BugReport(report))
    }
}