@file:JvmName("KovenantUtils")
package com.vfpowertech.keytap.core.kovenant

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred

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
