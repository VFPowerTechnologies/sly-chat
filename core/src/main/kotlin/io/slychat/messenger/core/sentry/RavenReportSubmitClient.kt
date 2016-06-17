package io.slychat.messenger.core.sentry

import io.slychat.messenger.core.http.HttpClientFactory
import java.net.ConnectException

class RavenReportSubmitClient(
    private val dsn: DSN,
    private val httpClientFactory: HttpClientFactory
) : ReportSubmitClient<ByteArray> {
    override fun submit(report: ByteArray): ReportSubmitError? {
        try {
            postEvent(dsn, httpClientFactory, report)
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

