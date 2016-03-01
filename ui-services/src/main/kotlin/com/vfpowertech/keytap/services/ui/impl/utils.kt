@file:JvmName("ImplUtils")
package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.KeyVaultStorage
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.http.api.ApiError
import com.vfpowertech.keytap.core.http.api.ApiResult
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

fun asyncUnlockKeyVault(keyVaultStorage: KeyVaultStorage, password: String): Promise<KeyVault, Exception> = task {
    KeyVault.Companion.fromStorage(keyVaultStorage, password)
}

fun asyncGenerateNewKeyVault(password: String): Promise<KeyVault, Exception> = task {
    generateNewKeyVault(password)
}

fun <T, R> ApiResult<T>.fold(leftBody: (ApiError) -> R, rightBody: (T) -> R): R {
    //constructor invariants make sure one of these is always non-null
    if (error != null)
        return leftBody(error!!)
    else
        return rightBody(value!!)
}

inline fun <T, R> ApiResult<T>.getOrThrow(body: (T) -> R): R =
    if (isError)
        throw RuntimeException(error!!.message)
    else
        body(value!!)
