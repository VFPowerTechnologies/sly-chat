package io.slychat.messenger.core.http.api.storage

import java.io.InputStream

class DownloadFileResponse(
    val contentLength: Long,
    val inputStream: InputStream
)