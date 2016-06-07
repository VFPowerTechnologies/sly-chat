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

    fun submit(builder: SentryEventBuilder) = synchronized(this) {
        val communicator = this.communicator ?: return

        val event = generateEvent(builder)

        val report = event.serialize()

        communicator.submit(report)
    }

    private fun generateEvent(builder: SentryEventBuilder): SentryEvent {
        val webViewVersion = this.webViewVersion
        if (webViewVersion != null)
            builder.withTag("webViewVersion", webViewVersion)

        val installationId = this.installationId
        if (installationId != null)
            builder.withTag("Installation ID", installationId)

        val userAddress = this.userAddress
        if (userAddress != null)
            builder.withUserInterface(userAddress.asString(), userAddress.id.long.toString())

        return builder.build()
    }
}