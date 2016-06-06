package io.slychat.messenger.core.sentry

sealed class ReporterMessage<out ReportType> {
    class BugReport<T>(val report: T) : ReporterMessage<T>()
    class Shutdown() : ReporterMessage<Nothing>()
    class NetworkStatus(val isAvailable: Boolean) : ReporterMessage<Nothing>()
}