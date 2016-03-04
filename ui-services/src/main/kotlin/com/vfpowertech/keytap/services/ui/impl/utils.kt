@file:JvmName("ImplUtils")
package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.KeyVaultStorage
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

fun asyncUnlockKeyVault(keyVaultStorage: KeyVaultStorage, password: String): Promise<KeyVault, Exception> = task {
    KeyVault.Companion.fromStorage(keyVaultStorage, password)
}

fun asyncGenerateNewKeyVault(password: String): Promise<KeyVault, Exception> = task {
    generateNewKeyVault(password)
}
