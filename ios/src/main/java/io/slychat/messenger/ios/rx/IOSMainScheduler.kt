package io.slychat.messenger.ios.rx

import apple.c.Globals
import rx.Scheduler
import rx.Subscription
import rx.exceptions.OnErrorNotImplementedException
import rx.functions.Action0
import rx.plugins.RxJavaPlugins
import rx.subscriptions.CompositeSubscription
import rx.subscriptions.SerialSubscription
import rx.subscriptions.Subscriptions
import java.util.concurrent.TimeUnit

//ignoring scheduler hooks for now
class IOSMainScheduler private constructor() : Scheduler() {
    companion object {
        val instance: IOSMainScheduler = IOSMainScheduler()
    }

    private class ScheduledAction(private val action: Action0) : Subscription, Runnable {
        private val sub = SerialSubscription()

        override fun isUnsubscribed(): Boolean {
            return sub.isUnsubscribed
        }

        override fun unsubscribe() {
            sub.unsubscribe()
        }

        fun set(subscription: Subscription) {
            sub.set(subscription)
        }

        override fun run() {
            if (sub.isUnsubscribed)
                return

            try {
                action.call()
            }
            catch (t: Throwable) {
                val ie = if (t is OnErrorNotImplementedException)
                    IllegalStateException("Exception thrown on worker thread. Add `onError` handling.", t)
                else
                    IllegalStateException("Fatal exception thrown on worker thread.", t)

                RxJavaPlugins.getInstance().errorHandler.handleError(ie)

                val thread = Thread.currentThread()
                thread.uncaughtExceptionHandler.uncaughtException(thread, ie)
            }
        }
    }

    private class IOSWorker : Worker() {
        private val subscriptions = CompositeSubscription()

        override fun schedule(action: Action0): Subscription {
            if (subscriptions.isUnsubscribed)
                return Subscriptions.unsubscribed()

            return schedule(action, 0, TimeUnit.MILLISECONDS)
        }

        override fun schedule(action: Action0, delayTime: Long, unit: TimeUnit): Subscription {
            if (subscriptions.isUnsubscribed)
                return Subscriptions.unsubscribed()

            val ns = TimeUnit.NANOSECONDS.convert(delayTime, unit)
            //we know DISPATCH_TIME_NOW is 0; we have no way to access the define
            val time = Globals.dispatch_time(0, ns)

            val scheduledAction = ScheduledAction(action)

            subscriptions.add(scheduledAction)

            //remove self when unsubscribed
            scheduledAction.set(Subscriptions.create {
                subscriptions.remove(scheduledAction)
            })

            val mainQueue = Globals.dispatch_get_main_queue()

            if (ns == 0L) {
                Globals.dispatch_async(mainQueue) {
                    scheduledAction.run()
                }
            }
            else {
                Globals.dispatch_after(time, mainQueue) {
                    scheduledAction.run()
                }
            }

            return scheduledAction
        }

        override fun isUnsubscribed(): Boolean {
            return subscriptions.isUnsubscribed
        }

        //no way to cancel something submitted via dispatch_after
        override fun unsubscribe() {
            subscriptions.unsubscribe()
        }
    }

    override fun createWorker(): Worker {
        return IOSWorker()
    }
}
