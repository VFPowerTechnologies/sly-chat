package io.slychat.messenger.services.files

import java.util.*

class TransferList<StatusT>(var maxSize: Int) {
    val queued = ArrayDeque<String>()
    val active = ArrayList<String>()
    //completed, errored uploads
    val inactive = ArrayList<String>()
    val all = HashMap<String, StatusT>()

    val canActivateMore: Boolean
        get() = hasQueued && active.size < maxSize

    val hasQueued: Boolean
        get() = queued.isNotEmpty()

    fun nextQueued(): StatusT {
        return getStatus(queued.pop())
    }

    fun setStatus(id: String, status: StatusT) {
        all[id] = status
    }

    fun getStatus(id: String): StatusT {
        return all[id] ?: error("Invalid id given: $id")
    }

    fun updateStatus(id: String, body: (StatusT) -> StatusT): StatusT {
        val status = getStatus(id)
        val updated = body(status)
        all[id] = updated
        return updated
    }
}