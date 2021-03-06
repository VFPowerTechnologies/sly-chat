package io.slychat.messenger.logger

import io.slychat.messenger.core.sentry.SentryEventBuilder

interface PlatformLogger {
    fun log(priority: LogPriority, loggerName: String, message: String)
    fun wtf(message: String)
    fun addBuilderProperties(builder: SentryEventBuilder)
}