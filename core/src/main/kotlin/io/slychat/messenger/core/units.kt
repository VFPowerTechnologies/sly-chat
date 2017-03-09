@file:JvmName("Units")
package io.slychat.messenger.core

//TODO kotlin 1.1 allows use to inline these
//this is a little abusive of syntax, but kotlin sadly doesn't have suffix functions

val Long.mb: Long
    get() {
        return this * 1024 * 1024
    }

val Int.mb: Int
    get() {
        return this * 1024 * 1024
    }

val Long.kb: Long
    get() {
        return this * 1024
    }

val Int.kb: Int
    get() {
        return this * 1024
    }
