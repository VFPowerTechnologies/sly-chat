package io.slychat.messenger.core.relay.base.java

internal sealed class ConnectionManagerMessage {
    class WriterError(val throwable: Throwable) : ConnectionManagerMessage() {
        override fun toString(): String {
            return "WriterError(throwable=$throwable)"
        }
    }

    class ReaderError(val throwable: Throwable) : ConnectionManagerMessage() {
        override fun toString(): String {
            return "ReaderError(throwable=$throwable)"
        }
    }

    class ReaderData(val bytes: ByteArray) : ConnectionManagerMessage()

    class Disconnect() : ConnectionManagerMessage()

    //sent from reader
    class EOF() : ConnectionManagerMessage()
}