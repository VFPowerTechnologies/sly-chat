package io.slychat.messenger.services.ui

import io.slychat.messenger.services.files.RemoteFileEvent

enum class UIRemoteFileEventType {
    ADD,
    DELETE,
    UPDATE
}

sealed class UIRemoteFileEvent {
    abstract val type: UIRemoteFileEventType

    class Added(val files: List<UIRemoteFile>) : UIRemoteFileEvent() {
        override val type: UIRemoteFileEventType
            get() = UIRemoteFileEventType.ADD
    }

    class Deleted(val files: List<UIRemoteFile>) : UIRemoteFileEvent() {
        override val type: UIRemoteFileEventType
            get() = UIRemoteFileEventType.DELETE
    }

    class Updated(val files: List<UIRemoteFile>) : UIRemoteFileEvent() {
        override val type: UIRemoteFileEventType
            get() = UIRemoteFileEventType.UPDATE
    }
}

fun RemoteFileEvent.toUI(): UIRemoteFileEvent {
    return when (this) {
        is RemoteFileEvent.Added -> UIRemoteFileEvent.Added(files.map { it.toUI() })
        is RemoteFileEvent.Deleted -> UIRemoteFileEvent.Deleted(files.map { it.toUI() })
        is RemoteFileEvent.Updated -> UIRemoteFileEvent.Updated(files.map { it.toUI() })
    }
}