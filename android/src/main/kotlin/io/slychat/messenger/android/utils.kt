@file:JvmName("AndroidUtils")
package io.slychat.messenger.android

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Run the given function in the main loop. */
fun androidRunInMain(context: Context?, body: () -> Unit) {
    val mainLooper = if (context != null)
        context.mainLooper
    else
        Looper.getMainLooper()

    Handler(mainLooper).post(body)
}
