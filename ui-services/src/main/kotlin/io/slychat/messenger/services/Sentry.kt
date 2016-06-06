package io.slychat.messenger.services

import io.slychat.messenger.core.sentry.ReportSubmitterCommunicator

object Sentry {
    private var communicator: ReportSubmitterCommunicator<ByteArray>? = null

    fun setCommunicator(communicator: ReportSubmitterCommunicator<ByteArray>) = synchronized(this) {
        this.communicator = communicator
    }

    fun submit(report: ByteArray) = synchronized(this) {
        val communicator = this.communicator ?: return

        communicator.submit(report)
    }
}