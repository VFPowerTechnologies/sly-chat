package io.slychat.messenger.core.sentry

import io.slychat.messenger.core.randomUUID
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
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

//from DefaultRavenFactory
private val ignoreTracesFrom = listOf(
    "com.sun.",
    "java.",
    "javax.",
    "org.omg.",
    "sun.",
    "junit.",
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

    fun build(): SentryEvent {
        return SentryEvent(
            randomUUID(),
            loggerName,
            level.toString().toLowerCase(),
            message,
            culprit,
            formatTimestamp(),
            messageInterface,
            exceptionInterface,
            mapOf(
                "osName" to System.getProperty("os.name"),
                "osVersion" to System.getProperty("os.version"),
                "arch" to System.getProperty("os.arch")
            ),
            mapOf(
                "Thread Name" to threadName
            )
        )
    }
}
