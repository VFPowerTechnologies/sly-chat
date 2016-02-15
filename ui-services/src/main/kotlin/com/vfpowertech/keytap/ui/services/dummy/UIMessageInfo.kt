package com.vfpowertech.keytap.ui.services.dummy

import com.vfpowertech.keytap.ui.services.UIContactDetails
import com.vfpowertech.keytap.ui.services.UIMessage

/** A message and its associated contact. */
data class UIMessageInfo(
    val contact: UIContactDetails,
    val message: UIMessage
)