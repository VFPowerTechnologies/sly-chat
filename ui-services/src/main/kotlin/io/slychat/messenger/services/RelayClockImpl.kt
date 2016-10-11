package io.slychat.messenger.services

import io.slychat.messenger.core.currentTimestamp
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

/**
 * @param differenceThreshold This is the (absolute) difference used before actual adjustment of the clock is performed.
 */
class RelayClockImpl(
    clockDifference: Observable<Long>,
    private val differenceThreshold: Long
) : RelayClock {
    private val log = LoggerFactory.getLogger(javaClass)

    private var clockDiff = 0L

    private val clockDiffSubject = BehaviorSubject.create<Long>(0)
    override val clockDiffUpdates: Observable<Long>
        get() = clockDiffSubject

    init {
        require(differenceThreshold >= 0) { "differenceMax must be a positive integer" }

        clockDifference
            .subscribe { onClockDifferenceUpdate(it) }
    }

    private fun onClockDifferenceUpdate(diff: Long) {
        setDifference(diff)
    }

    override fun currentTime(): Long {
        return currentTimestamp() + clockDiff
    }

    override fun setDifference(diff: Long) {
        clockDiff = if (Math.abs(diff) <= differenceThreshold) {
            log.debug("Difference is below threshold ({} < {}), ignoring", differenceThreshold, diff)
            0
        }
        else {
            log.debug("Clock adjusted by {}ms", diff)
            diff
        }

        clockDiffSubject.onNext(clockDiff)
    }
}