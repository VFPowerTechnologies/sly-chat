package io.slychat.messenger.ios.rx

import apple.c.Globals
import rx.Scheduler
import rx.Subscription
import rx.exceptions.OnErrorNotImplementedException
import rx.functions.Action0
import rx.plugins.RxJavaPlugins
import rx.subscriptions.Subscriptions
import java.util.concurrent.TimeUnit

//ignoring scheduler hooks for now
class IOSMainScheduler private constructor() : Scheduler() {
    companion object {
        val instance: IOSMainScheduler = IOSMainScheduler()
    }

    private class ScheduledAction(private val action: Action0) : Subscription, Runnable {
        @field:Volatile
        private var unsubscribed = false

        override fun isUnsubscribed(): Boolean {
            return unsubscribed
        }

        override fun unsubscribe() {
            unsubscribed = true
        }

        override fun run() {
            if (unsubscribed)
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
        @field:Volatile
        private var unsubscribed = false

        override fun schedule(action: Action0): Subscription {
            if (unsubscribed)
                return Subscriptions.unsubscribed()

            return schedule(action, 0, TimeUnit.MILLISECONDS)
        }

        override fun schedule(action: Action0, delayTime: Long, unit: TimeUnit): Subscription {
            if (unsubscribed)
                return Subscriptions.unsubscribed()

            //cheat since we know DISPATCH_TIME_NOW is 0; we have no way to access the const
            val ns = TimeUnit.NANOSECONDS.convert(delayTime, unit)

            val scheduledAction = ScheduledAction(action)

            val mainQueue = Globals.dispatch_get_main_queue()

            if (ns == 0L) {
                Globals.dispatch_async(mainQueue) {
                    scheduledAction.run()
                }
            }
            else {
                Globals.dispatch_after(ns, mainQueue) {
                    scheduledAction.run()
                }
            }

            return scheduledAction
        }

        override fun isUnsubscribed(): Boolean {
            return unsubscribed
        }

        override fun unsubscribe() {
            unsubscribed = true
            //no way to cancel something submitted via dispatch_after
        }
    }

    override fun createWorker(): Worker {
        return IOSWorker()
    }
}

