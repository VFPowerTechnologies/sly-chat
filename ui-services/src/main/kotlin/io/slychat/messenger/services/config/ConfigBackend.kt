package io.slychat.messenger.services.config

import com.fasterxml.jackson.databind.ObjectMapper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import java.io.File

interface ConfigBackend {
    fun update(o: Any)
    fun <T> read(clazz: Class<T>): Promise<T?, Exception>
}

class JsonConfigBackend(
    private val path: File,
    private val cipher: ConfigCipher
) : ConfigBackend {
    private val log = LoggerFactory.getLogger(javaClass)
    private var pending: Any? = null
    private var isWriting = false

    fun writeComplete() {
        isWriting = false

        val pending = this.pending

        if (pending != null) {
            doWrite(pending)
            this.pending = null
        }
    }

    private fun doWrite(o: Any) {
        isWriting = true

        log.info("Writing config file {}", path)
        task {
            val objectMapper = ObjectMapper()
            path.outputStream().use {
                val data = cipher.encrypt(objectMapper.writeValueAsBytes(o))
                it.write(data)
            }
        } successUi {
            log.info("Successful wrote config file {}", path)
            writeComplete()
        } failUi { e ->
            log.error("Unable to write {}: {}", path, e.message, e)
            writeComplete()
        }
    }

    override fun update(o: Any) {
        if (isWriting)
            pending = o
        else
            doWrite(o)
    }

    override fun <T> read(clazz: Class<T>): Promise<T?, Exception> {
        return task {
            try {
                path.inputStream().use {
                    val objectMapper = ObjectMapper()
                    val data = cipher.decrypt(it.readBytes())
                    objectMapper.readValue(data, clazz)
                }
            }
            catch (e: Exception) {
                log.warn("Unable to load config file {}: {}", path, e.message)
                null
            }
        }
    }
}
