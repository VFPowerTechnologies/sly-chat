package com.vfpowertech.keytap.services

import rx.Observable
import rx.Scheduler
import java.util.concurrent.ThreadLocalRandom
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
        waitTimeSeconds = ThreadLocalRandom.current().nextInt(0, exp.toInt())

        return Observable.timer(waitTimeSeconds.toLong(), TimeUnit.SECONDS).observeOn(scheduler)
    }

    fun reset() {
        n = 0
    }
}