package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.sentry.ReportSubmitterCommunicator
import io.slychat.messenger.core.sentry.SentryEvent
import io.slychat.messenger.core.sentry.serialize
import java.util.*

/** Frontend for the logger service, and for keeping extra environment info for sentry events. */
object Sentry {
    private var communicator: ReportSubmitterCommunicator<ByteArray>? = null
    private var webViewVersion: String? = null
    private var installationId: String? = null
    private var userAddress : SlyAddress? = null

    fun setCommunicator(communicator: ReportSubmitterCommunicator<ByteArray>) = synchronized(this) {
        this.communicator = communicator
    }

    fun setWebViewInfo(version: String) = synchronized(this) {
        webViewVersion = version
    }

    fun setInstallationId(installationId: String) = synchronized(this) {
        this.installationId = installationId
    }

    fun setUserAddress(userAddress: SlyAddress?) = synchronized(this) {
        this.userAddress = userAddress
    }

    fun submit(event: SentryEvent) = synchronized(this) {
        val event2 = addExtraInfo(event)

        val report = event2.serialize()
        val communicator = this.communicator ?: return

        communicator.submit(report)
    }

    private fun addExtraInfo(event: SentryEvent): SentryEvent {
        val tags = HashMap(event.tags)
        if (webViewVersion != null)
            tags["webViewVersion"] = webViewVersion

        val extra = HashMap(event.extra)
        if (installationId != null)
            extra["Installation ID"] = installationId

        val userAddress = this.userAddress
        if (userAddress != null) {
            extra["User ID"] = userAddress.id.long.toString()
            extra["Device ID"] = userAddress.deviceId.toString()
        }

        return event.copy(tags = tags, extra = extra)
    }
}