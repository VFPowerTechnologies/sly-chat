package io.slychat.messenger.logger

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

class LoggerFactory(
    private val defaultPriority: LogPriority,
    private val loggerPriorities: List<Pair<String, LogPriority>>,
    private val platformLogger: PlatformLogger
) : ILoggerFactory {
    private val loggerMap = ConcurrentHashMap<String, Logger>()

    private fun getPriorityFor(name: String): LogPriority {
        loggerPriorities.forEach { p ->
            val prefix = p.first
            if (name.startsWith(prefix))
                return p.second
        }

        return defaultPriority
    }

    override fun getLogger(name: String): Logger {
        //TODO generate shorter tag
        val logger = loggerMap[name]
        return if (logger == null) {
            val l = LoggerAdapter(name, getPriorityFor(name), platformLogger)
            loggerMap.putIfAbsent(name, l) ?: l
        }
        else
            logger
    }
}
