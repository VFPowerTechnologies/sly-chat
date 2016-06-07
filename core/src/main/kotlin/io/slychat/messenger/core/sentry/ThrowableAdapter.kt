package io.slychat.messenger.core.sentry

import java.util.*

//refactor stuff out
//no way to get the class to use
interface ThrowableAdapter {
    val underlying: Any

    val simpleName: String
    val message: String?
    val packageName: String?

    val stacktraceElements: Array<StackTraceElement>

    val next: ThrowableAdapter?
}

class ThrowableThrowableAdapter(
    override val underlying: Throwable
) : ThrowableAdapter {

    override val simpleName: String
        get() = underlying.javaClass.simpleName
    override val message: String?
        get() = underlying.message
    override val packageName: String?
        get() = underlying.javaClass.`package`?.name

    override val stacktraceElements: Array<StackTraceElement> = underlying.stackTrace

    override val next: ThrowableAdapter?
        get() {
            val cause = underlying.cause
            return if (cause != null)
                ThrowableThrowableAdapter(cause)
            else
                null
        }
}

//from DefaultRavenFactory plus a few extras
private val ignoreTracesFrom = listOf(
    "com.sun.",
    "java.",
    "javax.",
    "sun.",
    "android.",
    "com.android.",
    "com.intellij.rt."
)

fun extractCulprit(stackTraceElement: StackTraceElement): String {
    return "${stackTraceElement.className}.${stackTraceElement.methodName}(${stackTraceElement.fileName}:${stackTraceElement.lineNumber})"
}

fun extractStacktrace(throwableAdapter: ThrowableAdapter): StacktraceInterface {
    return StacktraceInterface(throwableAdapter.stacktraceElements.map { st ->
        val module = st.className
        val inApp = !ignoreTracesFrom.any { module.startsWith(it) }
        SentryStackFrame(
            st.fileName,
            module,
            inApp,
            st.methodName,
            st.lineNumber
        )
    }.reversed())
}


fun extractException(throwableAdapter: ThrowableAdapter, stacktrace: StacktraceInterface?): ExceptionInterface {
    return ExceptionInterface(
        throwableAdapter.simpleName,
        throwableAdapter.message,
        throwableAdapter.packageName,
        stacktrace
    )
}

//taken from SentryException.extractExceptionQueue
fun getExceptionInterface(throwableAdapter: ThrowableAdapter): Collection<ExceptionInterface> {
    val exceptions = ArrayDeque<ExceptionInterface>()
    val circularityDetector = HashSet<ThrowableAdapter>()

    var current: ThrowableAdapter? = throwableAdapter

    while (current != null && circularityDetector.add(current)) {
        val stacktrace = extractStacktrace(current)
        val exception = extractException(current, stacktrace)
        exceptions.add(exception)
        current = current.next
    }

    return exceptions
}

enum class LoggerLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
