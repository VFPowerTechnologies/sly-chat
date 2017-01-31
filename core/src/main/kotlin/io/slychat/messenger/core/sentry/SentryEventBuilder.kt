package io.slychat.messenger.core.sentry

import io.slychat.messenger.core.crypto.randomUUID
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.util.*

//https://docs.sentry.io/clientdev/
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
    private var userInterface: UserInterface? = null

    private var osName: String? = null
    private var osVersion: String? = null

    private val tags = HashMap<String, String>()
    private val extra = HashMap<String, String>()

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

    fun withUserInterface(id: String, username: String): SentryEventBuilder {
        userInterface = UserInterface(id, username)
        return this
    }

    fun withTag(k: String, v: String): SentryEventBuilder {
        tags[k] = v
        return this
    }

    fun withExtra(k: String, v: String): SentryEventBuilder {
        extra[k] = v
        return this
    }

    fun withOs(name: String, version: String): SentryEventBuilder {
        osName = name
        osVersion = version
        return this
    }

    fun build(): SentryEvent {
        val effectiveTags = HashMap(tags)

        effectiveTags["arch"] = System.getProperty("os.arch")

        if (osName != null) {
            effectiveTags["osName"] = osName

            if (osVersion != null)
                effectiveTags["osVersion"] = osVersion
        }

        val extra = HashMap(this.extra)
        extra[SentryEvent.EXTRA_THREAD_NAME] = threadName

        return SentryEvent(
            randomUUID(),
            loggerName,
            level.toString().toLowerCase(),
            message,
            culprit,
            formatTimestamp(),
            messageInterface,
            exceptionInterface,
            userInterface,
            effectiveTags,
            extra
        )
    }
}