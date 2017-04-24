package io.slychat.messenger.android.activites

import android.os.Bundle
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R

class ForgotPasswordActivity : BaseActivity() {
    companion object {
        val EXTRA_USERNAME = "io.slychat.messenger.android.activities.ForgotPasswordActivity.username"
        val EXTRA_EMAIL_FREED = "io.slychat.messenger.android.activities.ForgotPasswordActivity.email_freed"
        val EXTRA_PHONE_FREED = "io.slychat.messenger.android.activities.ForgotPasswordActivity.phone_freed"
        val START_REQUEST_FRAGMENT = "io.slychat.messenger.android.activities.StartResetRequestFragment"
    }

    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!successfullyLoaded)
            return

        app = AndroidApp.get(this)

        setContentView(R.layout.activity_forgot_password)

        var fragment = supportFragmentManager.findFragmentById(R.id.forgot_password_frag_container)
        if (fragment == null) {
            fragment = StartResetRequestFragment.getNewInstance()
            supportFragmentManager.beginTransaction().add(R.id.forgot_password_frag_container, fragment).commit()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }
}