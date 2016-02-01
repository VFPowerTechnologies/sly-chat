package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.UIContactInfo
import com.vfpowertech.keytap.ui.services.UIMessage

/** A message and its associated contact. */
data class UIMessageInfo(
    val contact: UIContactInfo,
    val message: UIMessage
)