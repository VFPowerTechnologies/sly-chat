package io.slychat.messenger.services.contacts

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import rx.Observable
import java.util.concurrent.TimeUnit

class RxTimerFactory : TimerFactory {
    //TODO CancelablePromise when we upgrade kovenant
    override fun run(timeout: Long, timeUnit: TimeUnit): Promise<Unit, Exception> {
        val observable = Observable.timer(timeout, timeUnit)

        val d = deferred<Unit, Exception>()

        observable.subscribe(
            {},
            { d.reject(RuntimeException("Timer failed", it)) },
            { d.resolve(Unit) }
        )

        return d.promise
    }
}