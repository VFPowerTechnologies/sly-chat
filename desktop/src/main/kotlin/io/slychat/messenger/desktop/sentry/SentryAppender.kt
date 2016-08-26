package io.slychat.messenger.desktop.sentry

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.core.sentry.LoggerLevel
import io.slychat.messenger.core.sentry.SentryEventBuilder
import io.slychat.messenger.core.sentry.extractCulprit
import io.slychat.messenger.services.Sentry
import org.slf4j.LoggerFactory

//AppenderBase is sychronized
class SentryAppender : AppenderBase<ILoggingEvent>() {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun logbackLevelToSentryLevel(level: Level): LoggerLevel = when (level) {
        Level.TRACE -> LoggerLevel.TRACE
        Level.DEBUG -> LoggerLevel.DEBUG
        Level.INFO -> LoggerLevel.INFO
        Level.WARN -> LoggerLevel.WARN
        Level.ERROR -> LoggerLevel.ERROR
        //only remaining are ALL/OFF, which aren't valid ILoggingEvent levels
        else -> throw IllegalArgumentException("Invalid logback log level: $level")
    }

    override fun append(eventObject: ILoggingEvent) {
        //this is here to prevent a misconfiguration from causing uploads of anything warning level
        if (!eventObject.level.isGreaterOrEqual(Level.WARN))
            return

        val culprit = if (eventObject.callerData.isNotEmpty())
            extractCulprit(eventObject.callerData[0])
        else
            eventObject.loggerName

        val level = logbackLevelToSentryLevel(eventObject.level)

        val builder = SentryEventBuilder(
            eventObject.loggerName,
            eventObject.threadName,
            level,
            eventObject.timeStamp,
            eventObject.formattedMessage,
            culprit
        )

        if (eventObject.throwableProxy != null) {
            val throwableAdapter = IThrowableProxyThrowableAdapter(eventObject.throwableProxy)
            builder.withExceptionInterface(throwableAdapter)
        }

        if (eventObject.argumentArray != null)
            builder.withMessageInterface(eventObject.message, eventObject.argumentArray.map { it?.toString() ?: "null" })

        builder.withOs(currentOs.name, currentOs.version)

        try {
            Sentry.submit(builder)
        }
        catch (t: Throwable) {
            //avoid calling back into here
            log.warn("Failed to submit bug report: {}", t.message, t)
        }
    }
}
