package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.services.ui.UIAccountModificationService
import com.vfpowertech.keytap.services.ui.UIUpdatePhoneInfo
import com.vfpowertech.keytap.services.ui.UIUpdatePhoneResult
import nl.komponents.kovenant.Promise

class UIAccountModificationServiceImpl(
        serverUrl: String
) : UIAccountModificationService {

    override fun updatePhone(info: UIUpdatePhoneInfo): Promise<UIUpdatePhoneResult, Exception> {
        return Promise.ofSuccess(UIUpdatePhoneResult(true, null, null));
    }
}