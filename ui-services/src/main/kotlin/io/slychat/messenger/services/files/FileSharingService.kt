package io.slychat.messenger.services.files

import nl.komponents.kovenant.Promise

interface FileSharingService {
    fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String): Promise<Unit, Exception>

    fun downloadFile(fileId: String, localFilePath: String): Promise<Unit, Exception>
}