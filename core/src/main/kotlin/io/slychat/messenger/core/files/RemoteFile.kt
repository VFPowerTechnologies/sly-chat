package io.slychat.messenger.core.files

import org.joda.time.DateTime

data class RemoteFile(
    val id: String,
    val shareKey: String,
    val lastUpdateVersion: Int,
    //only used for remote entries returned
    val isDeleted: Boolean,
    val userMetadata: UserMetadata,
    val creationDate: DateTime,
    val modificationDate: DateTime
)