package io.slychat.messenger.core.sentry

import io.slychat.messenger.core.randomUUID
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat

class SentryEventBuilder(
    val loggerName: String,
    val threadName: String,
    val level: LoggerLevel,
    val timestamp: Long,
    val message: String,
    val culprit: String
) {
    private var exceptionInterface: Collection<ExceptionInterface>? = null
    private var messageInterface: MessageInterface? = null

    private var osName: String? = null
    private var osVersion: String? = null

    private fun formatTimestamp(): String {
        val format = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(DateTimeZone.UTC)
        return format.print(timestamp)
    }

    fun withExceptionInterface(throwableAdapter: ThrowableAdapter): SentryEventBuilder {
        exceptionInterface = getExceptionInterface(throwableAdapter)

        return this
    }

    fun withMessageInterface(message: String, params: Collection<String>): SentryEventBuilder {
        messageInterface = MessageInterface(message, params)
        return this
    }

    fun withOs(name: String, version: String): SentryEventBuilder {
        osName = name
        osVersion = version
        return this
    }

    fun build(): SentryEvent {
        val tags = mutableMapOf(
            "arch" to System.getProperty("os.arch")
        )

        if (osName != null) {
            tags["osName"] = osName

            if (osVersion != null)
                tags["osVersion"] = osVersion
        }

        return SentryEvent(
            randomUUID(),
            loggerName,
            level.toString().toLowerCase(),
            message,
            culprit,
            formatTimestamp(),
            messageInterface,
            exceptionInterface,
            tags,
            mapOf(
                "Thread Name" to threadName
            )
        )
    }
}