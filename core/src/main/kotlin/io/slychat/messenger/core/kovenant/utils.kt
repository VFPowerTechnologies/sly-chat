@file:JvmName("KovenantUtils")
package io.slychat.messenger.core.kovenant

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.map

/** Recover from a failure. */

infix fun <V> Promise<V, Exception>.recover(body: (Exception) -> V): Promise<V, Exception> {
    val d = deferred<V, Exception>()

    success { d.resolve(it) }

    fail { e ->
        try {
            d.resolve(body(e))
        }
        catch (e: Exception) {
            d.reject(e)
        }
    }

    return d.promise
}

/** Recover from a given exception. */
infix inline fun <reified E : Exception, V> Promise<V, Exception>.recoverFor(crossinline body: (E) -> V): Promise<V, Exception> =
    recover { e ->
        if (e is E) body(e)
        else throw e
    }

/** Recover from a given exception. */
infix inline fun <reified E : Exception, V> Promise<V, Exception>.bindRecoverFor(crossinline body: (E) -> Promise<V, Exception>): Promise<V, Exception> =
    recoverWith { e ->
        if (e is E) body(e)
        else throw e
    }


infix fun <V> Promise<V, Exception>.recoverWith(body: (Exception) -> Promise<V, Exception>): Promise<V, Exception> {
    val d = deferred<V, Exception>()

    success { d.resolve(it) }

    fail { e ->
        try {
            body(e) map { d.resolve(it) } fail { d.reject(it) }
        }
        catch (e: Exception) {
            d.reject(e)
        }
    }

    return d.promise
}

infix fun <V> Promise<V, Exception>.fallbackTo(fallback: () -> Promise<V, Exception>): Promise<V, Exception> {
    val d = deferred<V, Exception>()

    success { d.resolve(it) }
    recoverWith { fallback() } success { d.resolve(it) } fail { d.reject(it) }

    return d.promise
}
