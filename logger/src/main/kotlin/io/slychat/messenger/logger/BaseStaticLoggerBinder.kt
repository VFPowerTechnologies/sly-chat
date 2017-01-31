package io.slychat.messenger.logger

import org.slf4j.ILoggerFactory
import org.slf4j.spi.LoggerFactoryBinder
import java.util.*

//http://slf4j.org/faq.html#slf4j_compatible
abstract class BaseStaticLoggerBinder : LoggerFactoryBinder {
    private val loggerFactoryClassStr = javaClass.name
    private var loggerFactory: LoggerFactory? = null

    private var defaultPriority = LogPriority.INFO
    private var loggerPriorities: List<Pair<String, LogPriority>> = emptyList()

    init {
        loadConfigFromProperties()
    }

    private fun logLevelFromString(s: String): LogPriority? {
        return when (s.toLowerCase()) {
            "trace" -> LogPriority.TRACE
            "debug" -> LogPriority.DEBUG
            "info" -> LogPriority.INFO
            "warn" -> LogPriority.WARN
            "error" -> LogPriority.ERROR
            else -> null
        }
    }

    private fun loadConfigFromProperties() {
        val properties = Properties()
        javaClass.getResourceAsStream("/sly-logger.properties").use {
            if (it == null)
                throw RuntimeException("/sly-logger.properties not found")
            
            properties.load(it)
        }

        //this is obviously fairly inefficient, but for a small number of items it's fine
        val priorities = ArrayList<Pair<String, LogPriority>>()

        properties.stringPropertyNames().forEach { k ->
            val v = properties.getProperty(k)
            val logLevel = logLevelFromString(v)
            if (logLevel != null) {
                if (k == "root")
                    defaultPriority = logLevel

                else if (k.startsWith("logger.")) {
                    val prefix = k.substring(7)
                    val priority = logLevel
                    priorities.add(prefix to priority)
                }
            }
        }

        loggerPriorities = priorities.sortedByDescending { it.first.length }
    }

    override fun getLoggerFactory(): ILoggerFactory {
        val loggerFactory = this.loggerFactory

        return if (loggerFactory == null) {
            val factory = LoggerFactory(defaultPriority, loggerPriorities, platformLogger)
            this.loggerFactory = factory
            factory
        }
        else
            loggerFactory
    }

    override fun getLoggerFactoryClassStr(): String {
        return loggerFactoryClassStr
    }

    abstract val platformLogger: PlatformLogger
}