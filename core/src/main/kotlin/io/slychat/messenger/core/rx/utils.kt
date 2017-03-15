@file:JvmName("RxUtils")
package io.slychat.messenger.core.rx

import rx.Observable
import rx.Subscriber
import rx.Subscription
import rx.subscriptions.CompositeSubscription

operator fun CompositeSubscription.plusAssign(subscription: Subscription) {
    add(subscription)
}

/** Wrapper function for calling onCompleted or orError depending on body outcome. */
inline fun <T> observable(crossinline body: (Subscriber<in T>) -> Unit): Observable<T> {
    return Observable.create {
        try {
            body(it)
            it.onCompleted()
        }
        catch (t: Throwable) {
            it.onError(t)
        }
    }
}
