package io.slychat.messenger.services.config

import com.fasterxml.jackson.databind.ObjectMapper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.alwaysUi
import org.slf4j.LoggerFactory

interface ConfigBackend {
    fun update(o: Any)
    fun <T> read(clazz: Class<T>): Promise<T?, Exception>
}

class JsonConfigBackend(
    private val configName: String,
    private val storage: ConfigStorage
) : ConfigBackend {
    private val log = LoggerFactory.getLogger(javaClass)
    private var pending: Any? = null
    private var isWriting = false

    private fun writeComplete() {
        isWriting = false

        val pending = this.pending

        if (pending != null) {
            doWrite(pending)
            this.pending = null
        }
    }

    private fun doWrite(o: Any) {
        isWriting = true

        task {
            val objectMapper = ObjectMapper()
            val data = objectMapper.writeValueAsBytes(o)
            storage.write(data)
        } alwaysUi {
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
            val objectMapper = ObjectMapper()
            val data = storage.read()
            if (data != null)
                objectMapper.readValue(data, clazz)
            else
                null
        }
    }
}
