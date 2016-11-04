package org.slf4j.impl

import android.os.Build
import android.util.Log
import io.slychat.messenger.core.sentry.SentryEventBuilder

class AndroidPlatformLogger : PlatformLogger {
    override fun log(priority: LogPriority, loggerName: String, message: String) {
        val pri = when (priority) {
            LogPriority.TRACE -> Log.VERBOSE
            LogPriority.DEBUG -> Log.DEBUG
            LogPriority.INFO -> Log.INFO
            LogPriority.WARN -> Log.WARN
            LogPriority.ERROR -> Log.ERROR
        }

        Log.println(pri, loggerName, message)
    }

    override fun wtf(message: String) {
        Log.wtf("LoggerAdapter", message)
    }

    override fun addBuilderProperties(builder: SentryEventBuilder) {
        builder.withOs("Android", Build.VERSION.RELEASE)
    }
}