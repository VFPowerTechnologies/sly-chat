package org.slf4j.impl

import org.slf4j.ILoggerFactory
import org.slf4j.spi.LoggerFactoryBinder
import java.util.*

@Suppress("unused")
//http://slf4j.org/faq.html#slf4j_compatible
object StaticLoggerBinder : LoggerFactoryBinder {
    //required; else you get a warning at runtime that your binding only supports <= 1.5.5
    //see http://slf4j.org/faq.html#version_checks
    @Suppress("unused")
    @JvmField
    val REQUESTED_API_VERSION: String = "1.6.99"

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

    //required
    @JvmStatic
    fun getSingleton(): StaticLoggerBinder {
        return this
    }

    override fun getLoggerFactory(): ILoggerFactory {
        val loggerFactory = this.loggerFactory

        return if (loggerFactory == null) {
            val factory = LoggerFactory(defaultPriority, loggerPriorities)
            this.loggerFactory = factory
            factory
        }
        else
            loggerFactory
    }

    override fun getLoggerFactoryClassStr(): String {
        return loggerFactoryClassStr
    }
}
