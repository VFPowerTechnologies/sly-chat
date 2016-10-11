package org.slf4j.impl

import android.os.Build
import android.util.Log
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.sentry.LoggerLevel
import io.slychat.messenger.core.sentry.SentryEventBuilder
import io.slychat.messenger.core.sentry.ThrowableThrowableAdapter
import io.slychat.messenger.core.sentry.extractCulprit
import io.slychat.messenger.services.Sentry
import org.slf4j.helpers.MarkerIgnoringBase
import org.slf4j.helpers.MessageFormatter

class LoggerAdapter(
    private val loggerName: String,
    private val allowedPriority: Int
) : MarkerIgnoringBase() {
    override fun getName(): String? {
        return loggerName
    }

    override fun warn(msg: String) {
        log(Log.WARN, msg, null)
    }

    override fun warn(format: String, arg: Any?) {
        formatAndLog(Log.WARN, format, arg)
    }

    override fun warn(format: String, vararg arguments: Any?) {
        formatAndLog(Log.WARN, format, *arguments)
    }

    override fun warn(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(Log.WARN, format, arg1, arg2)
    }

    override fun warn(msg: String, t: Throwable) {
        log(Log.WARN, msg, t)
    }

    override fun info(msg: String) {
        log(Log.INFO, msg, null)
    }

    override fun info(format: String, arg: Any?) {
        formatAndLog(Log.INFO, format, arg)
    }

    override fun info(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(Log.INFO, format, arg1, arg2)
    }

    override fun info(format: String, vararg arguments: Any?) {
        formatAndLog(Log.INFO, format, *arguments)
    }

    override fun info(msg: String, t: Throwable) {
        log(Log.INFO, msg, t)
    }

    override fun error(msg: String) {
        log(Log.ERROR, msg, null)
    }

    override fun error(format: String, arg: Any?) {
        formatAndLog(Log.ERROR, format, arg)
    }

    override fun error(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(Log.ERROR, format, arg1, arg2)
    }

    override fun error(format: String, vararg arguments: Any?) {
        formatAndLog(Log.ERROR, format, *arguments)
    }

    override fun error(msg: String, t: Throwable) {
        log(Log.ERROR, msg, t)
    }

    override fun debug(msg: String) {
        log(Log.DEBUG, msg, null)
    }

    override fun debug(format: String, arg: Any?) {
        formatAndLog(Log.DEBUG, format, arg)
    }

    override fun debug(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(Log.DEBUG, format, arg1, arg2)
    }

    override fun debug(format: String, vararg arguments: Any?) {
        formatAndLog(Log.DEBUG, format, *arguments)
    }

    override fun debug(msg: String, t: Throwable) {
        log(Log.DEBUG, msg, t)
    }

    override fun trace(msg: String) {
        log(Log.VERBOSE, msg, null)
    }

    override fun trace(format: String, arg: Any?) {
        formatAndLog(Log.VERBOSE, format, arg)
    }

    override fun trace(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(Log.VERBOSE, format, arg1, arg2)
    }

    override fun trace(format: String, vararg arguments: Any?) {
        formatAndLog(Log.VERBOSE, format, *arguments)
    }

    override fun trace(msg: String, t: Throwable) {
        log(Log.VERBOSE, msg, t)
    }

    override fun isErrorEnabled(): Boolean {
        return isLoggable(Log.ERROR)
    }

    override fun isDebugEnabled(): Boolean {
        return isLoggable(Log.DEBUG)
    }

    override fun isInfoEnabled(): Boolean {
        return isLoggable(Log.INFO)
    }

    override fun isWarnEnabled(): Boolean {
        return isLoggable(Log.WARN)
    }

    override fun isTraceEnabled(): Boolean {
        return isLoggable(Log.VERBOSE)
    }

    //on android, checks Log.isLoggable
    private fun isLoggable(priority: Int): Boolean {
        return priority >= allowedPriority
    }

    private fun formatAndLog(priority: Int, format: String, vararg arguments: Any?) {
        if (!isLoggable(priority))
            return
        val ft = MessageFormatter.arrayFormat(format, arguments)
        logInternal(priority, ft.message, ft.throwable)
    }

    private fun log(priority: Int, message: String, throwable: Throwable?) {
        if (isLoggable(priority)) {
            logInternal(priority, message, throwable)
        }
    }

    private fun getCulpritFromStacktrace(): String {
        val stackTraceElements = Thread.currentThread().stackTrace
        if (stackTraceElements.size < 7)
            return loggerName

        //VMSTack.getThreadStackTrace
        //Thread.getStackTrace
        //getCulpritFromStacktrace
        //logInternal
        //log
        //error|info|etc
        //<actual caller>
        val caller = stackTraceElements[6]

        return extractCulprit(caller)
    }

    private fun androidLevelToSentryLevel(level: Int): LoggerLevel = when (level) {
        Log.VERBOSE -> LoggerLevel.TRACE
        Log.DEBUG -> LoggerLevel.DEBUG
        Log.INFO -> LoggerLevel.INFO
        Log.WARN -> LoggerLevel.WARN
        Log.ERROR -> LoggerLevel.ERROR
        else -> throw IllegalArgumentException("Invalid log level: $level")
    }

    private fun logInternal(priority: Int, message: String, throwable: Throwable?) {
        val m = if (throwable != null) {
            val s = Log.getStackTraceString(throwable)
            message + "\n" + s
        }
        else
            message

        Log.println(priority, loggerName, m)

        //hacky
        if (priority == Log.ERROR) {
            val currentThread = Thread.currentThread()

            val threadName = currentThread.name
            val timestamp = currentTimestamp()
            //WARNING don't move this into another function call without editting the stacktrace logic in the function
            val culprit = getCulpritFromStacktrace()
            val level = androidLevelToSentryLevel(priority)

            val builder = SentryEventBuilder(
                loggerName,
                threadName,
                level,
                timestamp,
                message,
                culprit
            )

            if (throwable != null)
                builder.withExceptionInterface(ThrowableThrowableAdapter(throwable))

            builder.withOs("Android", Build.VERSION.RELEASE)

            try {
                Sentry.submit(builder)
            }
            catch (t: Throwable) {
                Log.wtf("LoggerAdapter", "Failed to submit bug report: ${t.message}")
            }
        }
    }
}
