package io.slychat.messenger.core.sentry

import java.net.ConnectException

class RavenReportSubmitClient(private val dsn: DSN) : ReportSubmitClient<ByteArray> {
    override fun submit(report: ByteArray): ReportSubmitError? {
        try {
            postEvent(dsn, report)
            return null
        }
        catch (e: ConnectException) {
            return ReportSubmitError.Recoverable(e.message, e)
        }
        catch (e: SentryException) {
            if (e.isRecoverable)
                return ReportSubmitError.Recoverable(e.message, e)
            else
                return ReportSubmitError.Fatal(e.message, e)
        }
        catch (t: Throwable) {
            return ReportSubmitError.Fatal(t.message, t)
        }
    }
}

