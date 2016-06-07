package io.slychat.messenger.desktop.sentry

import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.StackTraceElementProxy
import io.slychat.messenger.core.sentry.ThrowableAdapter

private fun Array<StackTraceElementProxy>.toStackTraceElementArray(): Array<StackTraceElement> {
    return map { it.stackTraceElement }.toTypedArray()
}

//same as raven-java
private val DEFAULT_PACKAGE = "(default)"

fun splitClassName(fqcn: String): Pair<String, String> {
    val pos = fqcn.indexOfLast { it == '.' }
    return if (pos == -1)
        DEFAULT_PACKAGE to fqcn
    else {
        val packageName = fqcn.substring(0, pos)
        val className = fqcn.substring(pos+1)

        packageName to className
    }
}

//logback doesn't hold on to Throwables
class IThrowableProxyThrowableAdapter(
    override val underlying: IThrowableProxy
) : ThrowableAdapter {
    override val simpleName: String
    override val packageName: String?
    override val message: String?
        get() = underlying.message
    override val stacktraceElements: Array<StackTraceElement>
        get() = underlying.stackTraceElementProxyArray.toStackTraceElementArray()

    override val next: ThrowableAdapter?
        get() {
            return if (underlying.cause != null)
                IThrowableProxyThrowableAdapter(underlying.cause)
            else
                null
        }

    init {
        val (packageName, simpleName) = splitClassName(underlying.className)

        this.simpleName = simpleName
        this.packageName = packageName

    }
}

