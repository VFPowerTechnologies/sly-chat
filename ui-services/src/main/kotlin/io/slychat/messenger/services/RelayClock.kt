package io.slychat.messenger.services

import rx.Observable

interface RelayClock {
    /** Current (approximate) relay time. */
    fun currentTime(): Long

    /** Set the current difference. */
    fun setDifference(diff: Long)

    /** The current difference between system and relay time. Mostly here for exporting value to the ui layer. */
    val clockDiffUpdates: Observable<Long>
}
