package io.slychat.messenger.ios.kovenant

import apple.c.Globals
import apple.foundation.NSThread
import nl.komponents.kovenant.ProcessAwareDispatcher

class IOSDispatcher private constructor() : ProcessAwareDispatcher {
    companion object {
        val instance: IOSDispatcher = IOSDispatcher()
    }

    override fun ownsCurrentProcess(): Boolean {
        return NSThread.currentThread().isMainThread
    }

    override fun offer(task: () -> Unit): Boolean {
        Globals.dispatch_async(Globals.dispatch_get_main_queue()) {
            task()
        }

        return true
    }
}
