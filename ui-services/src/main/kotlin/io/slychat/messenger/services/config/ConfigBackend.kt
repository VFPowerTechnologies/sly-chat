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

//TODO use a function that generates an Input/OutputStream? so we can use Cipher*Stream
class JsonConfigBackend(private val path: File) : ConfigBackend {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()
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
            path.outputStream().use {
                objectMapper.writeValue(it, o)
            }
        } successUi {
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
                    objectMapper.readValue(it, clazz)
                }
            }
            catch (e: Exception) {
                null
            }
        }
    }
}
