package io.slychat.messenger.core.sentry

interface ReportSubmitError {
    //something unrecoverable happened; malformed request, invalid response, invalid api key, etc
    class Fatal(val message: String?, val cause: Throwable) : ReportSubmitError
    //timeout/host unreachable/etc; retry later
    class Recoverable(val message: String?, val cause: Throwable) : ReportSubmitError
}