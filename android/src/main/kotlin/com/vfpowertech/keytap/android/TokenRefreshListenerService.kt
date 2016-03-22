package com.vfpowertech.keytap.android

import com.google.android.gms.iid.InstanceIDListenerService

class TokenRefreshListenerService : InstanceIDListenerService() {
    override fun onTokenRefresh() {
        val app = AndroidApp.get(this)
        app.onGCMTokenRefreshRequired()
    }
}