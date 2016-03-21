package com.vfpowertech.keytap.services.ui

import com.vfpowertech.keytap.core.persistence.AccountInfo

data class UILoginResult(
    val successful: Boolean,
    val accountInfo: AccountInfo?,
    val errorMessage: String?
)