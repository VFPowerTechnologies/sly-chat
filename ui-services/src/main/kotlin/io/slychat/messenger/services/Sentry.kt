package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.sentry.ReportSubmitter
import io.slychat.messenger.core.sentry.SentryEvent
import io.slychat.messenger.core.sentry.SentryEventBuilder
import io.slychat.messenger.core.sentry.serialize

/** Frontend for the logger service, and for keeping extra environment info for sentry events. */
object Sentry {
    private var reportSubmitter: ReportSubmitter<ByteArray>? = null

    //tags
    private var userAddress: SlyAddress? = null
    private var androidDeviceName: String? = null
    private var iosDeviceName: String? = null
    private var buildNumber: String? = null

    //context
    private var isUiVisible = false
    private var isNetworkAvailable = false

    fun setReportSubmitter(reportSubmitter: ReportSubmitter<ByteArray>) = synchronized(this) {
        this.reportSubmitter = reportSubmitter
    }

    fun setUserAddress(userAddress: SlyAddress?) = synchronized(this) {
        this.userAddress = userAddress
    }

    fun setAndroidDeviceName(androidDeviceName: String) = synchronized(this) {
        this.androidDeviceName = androidDeviceName
    }

    fun setIOSDeviceName(iosDeviceName: String) = synchronized(this) {
        this.iosDeviceName = iosDeviceName
    }

    fun setBuildNumber(buildNumber: String) {
        this.buildNumber = buildNumber
    }

    fun setIsUiVisible(isVisible: Boolean) = synchronized(this) {
        isUiVisible = isVisible
    }

    fun setIsNetworkAvailable(isAvailable: Boolean) = synchronized(this) {
        isNetworkAvailable = isAvailable
    }

    fun submit(builder: SentryEventBuilder) = synchronized(this) {
        val reportSubmitter = this.reportSubmitter ?: return

        val event = generateEvent(builder)

        val report = event.serialize()

        reportSubmitter.submit(report)
    }

    private fun generateEvent(builder: SentryEventBuilder): SentryEvent {
        androidDeviceName?.apply {
            builder.withTag("androidDeviceName", this)
        }

        iosDeviceName?.apply {
            builder.withTag("iosDeviceName", this)
        }

        userAddress?.apply {
            builder.withUserInterface(this.asString(), this.id.long.toString())
        }

        buildNumber?.apply {
            builder.withTag("buildNumber", this)
        }

        builder.withExtra(SentryEvent.EXTRA_IS_UI_VISIBLE, isUiVisible.toString())
        builder.withExtra(SentryEvent.EXTRA_IS_NETWORK_AVAILABLE, isNetworkAvailable.toString())

        return builder.build()
    }

    /** Waits for the report submitter to shut down. Used on crashes to ensure proper flush to disk of crash report. */
    fun waitForShutdown() = synchronized(this) {
        val reportSubmitter = this.reportSubmitter ?: return

        reportSubmitter.shutdown()
        reportSubmitter.shutdownPromise.get()
    }
}