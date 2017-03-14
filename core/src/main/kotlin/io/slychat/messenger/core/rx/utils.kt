@file:JvmName("RxUtils")
package io.slychat.messenger.core.rx

import rx.Subscription
import rx.subscriptions.CompositeSubscription

operator fun CompositeSubscription.plusAssign(subscription: Subscription) {
    add(subscription)
}
