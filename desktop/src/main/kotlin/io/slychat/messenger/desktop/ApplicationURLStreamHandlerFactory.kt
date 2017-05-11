package io.slychat.messenger.desktop

import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.files.cache.AttachmentService
import org.slf4j.LoggerFactory
import rx.Observable
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

/**
 * Supported protocols:
 *
 * attachment://<fileId>?res=<resolution>
 *     If the res parameter is omitted or is 0, returns a stream to the full-sized image.
 */
class ApplicationURLStreamHandlerFactory(
    userSessionAvailable: Observable<UserComponent?>
) : URLStreamHandlerFactory {
    private class AttachmentURLConnection(url: URL, private val attachmentService: AttachmentService) : URLConnection(url) {
        private val log = LoggerFactory.getLogger(javaClass)

        private var isConnected = false
        private var inputStream: InputStream? = null

        private fun parseQueryParams(query: String): List<Pair<String, String>> {
            return query.split('&').map {
                val parts = it.split('=', limit = 2)
                require(parts.size == 2) { "Invalid query syntax" }
                parts[0] to parts[1]
            }
        }

        private fun getResolution(url: URL): Int {
            val query = url.query ?: return 0

            val res = parseQueryParams(query).find { it.first == "res" } ?: return 0

            return Integer.parseInt(res.second)
        }

        override fun connect() {
            if (isConnected)
                return

            val fileId = url.host
            val resolution = getResolution(url)

            log.debug("Requesting: {} @ {}", fileId, resolution)

            val p = if (resolution == 0)
                attachmentService.getImageStream(fileId)
            else
                attachmentService.getThumbnailStream(fileId, resolution)

            //TODO show a diff image indicating the user deleted the file if result.isDeleted is true
            val maybeInputStream = p.get().inputStream

            if (maybeInputStream != null) {
                inputStream = maybeInputStream
                log.debug("{} is available", fileId)
            }
            else {
                //TODO pick proper resolution image
                inputStream = javaClass.getResourceAsStream("/placeholder_200x200.png");
                log.debug("{} is not available, returning placeholder", fileId)
            }

            isConnected = true
        }

        override fun getInputStream(): InputStream {
            return inputStream ?: throw IllegalStateException("Input stream not available")
        }
    }

    private class AttachmentURLStreamHandler(userSessionAvailable: Observable<UserComponent?>) : URLStreamHandler() {
        private var attachmentService: AttachmentService? = null

        init {
            userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
        }

        override fun openConnection(url: URL): URLConnection {
            val attachmentService = this.attachmentService ?: error("Attempt to access attachment while logged off")

            return AttachmentURLConnection(url, attachmentService)
        }

        private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
            if (userComponent == null)
                attachmentService = null
            else
                attachmentService = userComponent.attachmentService
        }
    }

    private val attachmentHandler = AttachmentURLStreamHandler(userSessionAvailable)

    //the handler is cached internally once returned
    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        return if (protocol == "attachment")
            attachmentHandler
        else
            null
    }
}
