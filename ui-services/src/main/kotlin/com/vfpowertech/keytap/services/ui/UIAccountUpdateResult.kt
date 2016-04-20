package com.vfpowertech.keytap.services.ui

import com.vfpowertech.keytap.core.persistence.AccountInfo

/**
 * @property accountInfo New account information, may be null.
 * @property successful Whether or not the update was successful. When false, errorMessage is non-null, and validationErrors may be non-null.
 * @property errorMessage Only set when successful is false.
 */
data class UIAccountUpdateResult(
    val accountInfo: AccountInfo?,
    val successful: Boolean,
    val errorMessage: String?
)