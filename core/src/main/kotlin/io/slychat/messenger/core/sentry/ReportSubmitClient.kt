package io.slychat.messenger.core.sentry

interface ReportSubmitClient<ReportType> {
    /** Must not throw any exceptions. */
    fun submit(report: ReportType): ReportSubmitError?
}