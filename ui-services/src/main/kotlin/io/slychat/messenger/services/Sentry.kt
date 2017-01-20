package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.sentry.ReportSubmitterCommunicator
import io.slychat.messenger.core.sentry.SentryEvent
import io.slychat.messenger.core.sentry.SentryEventBuilder
import io.slychat.messenger.core.sentry.serialize

/** Frontend for the logger service, and for keeping extra environment info for sentry events. */
object Sentry {
    private var communicator: ReportSubmitterCommunicator<ByteArray>? = null
    private var webViewVersion: String? = null
    private var userAddress: SlyAddress? = null
    private var androidDeviceName: String? = null
    private var iosDeviceName: String? = null
    private var buildNumber: String? = null

    fun setCommunicator(communicator: ReportSubmitterCommunicator<ByteArray>) = synchronized(this) {
        this.communicator = communicator
    }

    fun setWebViewInfo(version: String) = synchronized(this) {
        webViewVersion = version
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

    fun submit(builder: SentryEventBuilder) = synchronized(this) {
        val communicator = this.communicator ?: return

        val event = generateEvent(builder)

        val report = event.serialize()

        communicator.submit(report)
    }

    private fun generateEvent(builder: SentryEventBuilder): SentryEvent {
        webViewVersion?.apply {
            builder.withTag("webViewVersion", this)
        }

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

        return builder.build()
    }

    /** Waits for the report submitter to shut down. Used on crashes to ensure proper flush to disk of crash report. */
    fun waitForShutdown() = synchronized(this) {
        val communicator = this.communicator ?: return

        communicator.shutdown()
        communicator.shutdownPromise.get()
    }
}