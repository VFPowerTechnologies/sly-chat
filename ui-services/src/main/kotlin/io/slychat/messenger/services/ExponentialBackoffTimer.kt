package io.slychat.messenger.services

import rx.Observable
import rx.Scheduler
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Simple exponential backoff timer.
 *
 * @param scheduler Scheduler to run observables on.
 * @param maxN Maximum power of 2 for determining upper bound of next timer.
 **/
class ExponentialBackoffTimer(private val scheduler: Scheduler, private val maxN: Int = 7) {
    private var n = 0

    /** Wait time in seconds of last returned timer. */
    var waitTimeSeconds: Int = 0
        private set

    fun next(): Observable<Long> {
        if (n < maxN)
            n += 1

        val exp = Math.pow(2.0, n.toDouble())
        //can't use ThreadLocalRandom since it's only available in API level 21
        waitTimeSeconds = Random().nextInt(exp.toInt() + 1)

        return Observable.timer(waitTimeSeconds.toLong(), TimeUnit.SECONDS).observeOn(scheduler)
    }

    fun reset() {
        n = 0
    }
}