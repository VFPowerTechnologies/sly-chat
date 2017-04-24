package io.slychat.messenger.services.files.cache

import nl.komponents.kovenant.Promise

interface ThumbnailGenerator {
    fun generateThumbnails(fileId: String): Promise<Unit, Exception>
}