package org.slf4j.impl

import io.slychat.messenger.core.currentOs
import io.slychat.messenger.core.sentry.SentryEventBuilder
import io.slychat.messenger.logger.LogPriority
import io.slychat.messenger.logger.PlatformLogger
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

internal class DesktopPlatformLogger : PlatformLogger {
    private val dateTimeFormatter = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss")

    private fun priorityToString(priority: LogPriority): String = when (priority) {
        LogPriority.TRACE -> "T"
        LogPriority.DEBUG -> "D"
        LogPriority.INFO -> "I"
        LogPriority.WARN -> "W"
        LogPriority.ERROR -> "E"
    }
    override fun log(priority: LogPriority, loggerName: String, message: String) {
        val p = priorityToString(priority)

        val t = dateTimeFormatter.print(DateTime())

        val l = loggerName.split('.').last()

        println("[$p] [$t] [$l] $message")
    }

    override fun wtf(message: String) {
        System.err.println("An unexpected error occured: $message")
    }

    override fun addBuilderProperties(builder: SentryEventBuilder) {
        builder.withOs(currentOs.name, currentOs.version)
    }
}