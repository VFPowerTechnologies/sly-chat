package io.slychat.messenger.services

import io.slychat.messenger.core.sentry.ReportSubmitterCommunicator
import io.slychat.messenger.core.sentry.SentryEvent
import io.slychat.messenger.core.sentry.serialize

object Sentry {
    private var communicator: ReportSubmitterCommunicator<ByteArray>? = null

    fun setCommunicator(communicator: ReportSubmitterCommunicator<ByteArray>) = synchronized(this) {
        this.communicator = communicator
    }

    fun submit(event: SentryEvent) = synchronized(this) {
        val report = event.serialize()
        val communicator = this.communicator ?: return

        communicator.submit(report)
    }
}