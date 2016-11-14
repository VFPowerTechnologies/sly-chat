package io.slychat.messenger.logger

import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.sentry.LoggerLevel
import io.slychat.messenger.core.sentry.SentryEventBuilder
import io.slychat.messenger.core.sentry.ThrowableThrowableAdapter
import io.slychat.messenger.core.sentry.extractCulprit
import io.slychat.messenger.services.Sentry
import org.slf4j.helpers.MarkerIgnoringBase
import org.slf4j.helpers.MessageFormatter
import java.io.PrintWriter
import java.io.StringWriter

class LoggerAdapter(
    private val loggerName: String,
    private val allowedPriority: LogPriority,
    private val platformLogger: PlatformLogger
) : MarkerIgnoringBase() {
    override fun getName(): String? {
        return loggerName
    }

    override fun warn(msg: String) {
        log(LogPriority.WARN, msg, null)
    }

    override fun warn(format: String, arg: Any?) {
        formatAndLog(LogPriority.WARN, format, arg)
    }

    override fun warn(format: String, vararg arguments: Any?) {
        formatAndLog(LogPriority.WARN, format, *arguments)
    }

    override fun warn(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(LogPriority.WARN, format, arg1, arg2)
    }

    override fun warn(msg: String, t: Throwable) {
        log(LogPriority.WARN, msg, t)
    }

    override fun info(msg: String) {
        log(LogPriority.INFO, msg, null)
    }

    override fun info(format: String, arg: Any?) {
        formatAndLog(LogPriority.INFO, format, arg)
    }

    override fun info(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(LogPriority.INFO, format, arg1, arg2)
    }

    override fun info(format: String, vararg arguments: Any?) {
        formatAndLog(LogPriority.INFO, format, *arguments)
    }

    override fun info(msg: String, t: Throwable) {
        log(LogPriority.INFO, msg, t)
    }

    override fun error(msg: String) {
        log(LogPriority.ERROR, msg, null)
    }

    override fun error(format: String, arg: Any?) {
        formatAndLog(LogPriority.ERROR, format, arg)
    }

    override fun error(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(LogPriority.ERROR, format, arg1, arg2)
    }

    override fun error(format: String, vararg arguments: Any?) {
        formatAndLog(LogPriority.ERROR, format, *arguments)
    }

    override fun error(msg: String, t: Throwable) {
        log(LogPriority.ERROR, msg, t)
    }

    override fun debug(msg: String) {
        log(LogPriority.DEBUG, msg, null)
    }

    override fun debug(format: String, arg: Any?) {
        formatAndLog(LogPriority.DEBUG, format, arg)
    }

    override fun debug(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(LogPriority.DEBUG, format, arg1, arg2)
    }

    override fun debug(format: String, vararg arguments: Any?) {
        formatAndLog(LogPriority.DEBUG, format, *arguments)
    }

    override fun debug(msg: String, t: Throwable) {
        log(LogPriority.DEBUG, msg, t)
    }

    override fun trace(msg: String) {
        log(LogPriority.TRACE, msg, null)
    }

    override fun trace(format: String, arg: Any?) {
        formatAndLog(LogPriority.TRACE, format, arg)
    }

    override fun trace(format: String, arg1: Any?, arg2: Any?) {
        formatAndLog(LogPriority.TRACE, format, arg1, arg2)
    }

    override fun trace(format: String, vararg arguments: Any?) {
        formatAndLog(LogPriority.TRACE, format, *arguments)
    }

    override fun trace(msg: String, t: Throwable) {
        log(LogPriority.TRACE, msg, t)
    }

    override fun isErrorEnabled(): Boolean {
        return isLoggable(LogPriority.ERROR)
    }

    override fun isDebugEnabled(): Boolean {
        return isLoggable(LogPriority.DEBUG)
    }

    override fun isInfoEnabled(): Boolean {
        return isLoggable(LogPriority.INFO)
    }

    override fun isWarnEnabled(): Boolean {
        return isLoggable(LogPriority.WARN)
    }

    override fun isTraceEnabled(): Boolean {
        return isLoggable(LogPriority.TRACE)
    }

    //on android, checks LogPriority.isLoggable
    private fun isLoggable(priority: LogPriority): Boolean {
        return priority >= allowedPriority
    }

    private fun formatAndLog(priority: LogPriority, format: String, vararg arguments: Any?) {
        if (!isLoggable(priority))
            return
        val ft = MessageFormatter.arrayFormat(format, arguments)
        logInternal(priority, ft.message, ft.throwable)
    }

    private fun log(priority: LogPriority, message: String, throwable: Throwable?) {
        if (isLoggable(priority)) {
            logInternal(priority, message, throwable)
        }
    }

    private fun getCulpritFromStacktrace(): String {
        val stackTraceElements = Thread.currentThread().stackTrace

        var culpritOffset = 5

        //adjustment for android
        if (stackTraceElements[0].className == "dalvik.system.VMStack")
            culpritOffset += 1

        if (culpritOffset >= stackTraceElements.size)
            return loggerName

        //VMSTack.getThreadStackTrace (android only)
        //Thread.getStackTrace
        //getCulpritFromStacktrace
        //logInternal
        //log
        //error|info|etc
        //<actual caller>
        val caller = stackTraceElements[culpritOffset]

        return extractCulprit(caller)
    }

    private fun logPriorityToSentryLevel(priority: LogPriority): LoggerLevel = when (priority) {
        LogPriority.TRACE -> LoggerLevel.TRACE
        LogPriority.DEBUG -> LoggerLevel.DEBUG
        LogPriority.INFO -> LoggerLevel.INFO
        LogPriority.WARN -> LoggerLevel.WARN
        LogPriority.ERROR -> LoggerLevel.ERROR
    }

    private fun getStackTraceAsString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    private fun logInternal(priority: LogPriority, message: String, throwable: Throwable?) {
        val m = if (throwable != null) {
            val s = getStackTraceAsString(throwable)
            message + "\n" + s
        }
        else
            message

        platformLogger.log(priority, loggerName, m)

        //hacky
        if (priority == LogPriority.ERROR) {
            val currentThread = Thread.currentThread()

            val threadName = currentThread.name
            val timestamp = currentTimestamp()
            //WARNING don't move this into another function call without editting the stacktrace logic in the function
            val culprit = getCulpritFromStacktrace()
            val level = logPriorityToSentryLevel(priority)

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

            platformLogger.addBuilderProperties(builder)

            try {
                Sentry.submit(builder)
            }
            catch (t: Throwable) {
                platformLogger.wtf("Failed to submit bug report: ${t.message}")
            }
        }
    }
}
