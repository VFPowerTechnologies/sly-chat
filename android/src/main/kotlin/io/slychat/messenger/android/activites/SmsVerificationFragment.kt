package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class SmsVerificationFragment: Fragment() {
    companion object {
        val EXTRA_USERNAME = "io.slychat.messenger.android.activities.SmsVerificationFragment.username"
        val EXTRA_PASSWORD = "io.slychat.messenger.android.activities.SmsVerificationFragment.password"
        val EXTRA_REMEMBER_ME = "io.slychat.messenger.android.activities.SmsVerificationFragment.rememberMe"

        val INVALID_CODE_ERROR = "invalid code"

        fun getNewInstance(username: String, password: String, rememberMe: Boolean): Fragment {
            val fragment = SmsVerificationFragment()
            val bundle = Bundle()
            bundle.putString(EXTRA_USERNAME, username)
            bundle.putString(EXTRA_PASSWORD, password)
            bundle.putBoolean(EXTRA_REMEMBER_ME, rememberMe)

            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()
            fragment.arguments = bundle

            return fragment
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private var v: View? = null

    private lateinit var email: String
    private lateinit var password: String

    private var rememberMe = false

    private lateinit var registrationActivity: RegistrationActivity

    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registrationActivity = activity as RegistrationActivity

        email = this.arguments[EXTRA_USERNAME] as String
        password = this.arguments[EXTRA_PASSWORD] as String
        rememberMe = this.arguments[EXTRA_REMEMBER_ME] as Boolean
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View ? {
        v = inflater?.inflate(R.layout.sms_verification_fragment, container, false)
        app = AndroidApp.get(activity)

        val smsSubmitBtn = v?.findViewById(R.id.submit_step_five) as Button
        val updatePhoneLink = v?.findViewById(R.id.sms_verification_update_phone_link) as TextView
        val resendCodeLink = v?.findViewById(R.id.sms_verification_resend_link) as TextView

        smsSubmitBtn.setOnClickListener {
            handleSmsSubmit()
        }

        updatePhoneLink.setOnClickListener {
            val fragment = UpdatePhoneFragment.getNewInstance(email, password)
            fragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack(SmsVerificationFragment::class.java.name).commit()
        }

        resendCodeLink.setOnClickListener {
            app.appComponent.registrationService.resendVerificationCode(email)
        }


        return v
    }

    private fun handleSmsSubmit () {
        val smsCodeField = v?.findViewById(R.id.sms_verification_code_field) as EditText
        val code = smsCodeField.text.toString()
        if (code.isEmpty()) {
            smsCodeField.error = resources.getString(R.string.registration_verification_code_required_error)
            return
        }

        registrationActivity.showProgressDialog(resources.getString(R.string.registration_sms_verification_process))
        app.appComponent.registrationService.submitVerificationCode(email, code) successUi  { result ->
            if (result.successful) {
                registrationActivity.setProgressDialogMessage(resources.getString(R.string.login_in_process_message))
                app.app.login(email, password, rememberMe)
            }
            else {
                registrationActivity.hideProgressDialog()
                if (result.errorMessage != null && result.errorMessage == INVALID_CODE_ERROR) {
                    smsCodeField.error = resources.getString(R.string.registration_verification_code_invalid_error)
                }
            }
        } failUi {
            registrationActivity.hideProgressDialog()
            log.debug(it.message, it.stackTrace)
        }
    }


    override fun onPause() {
        log.debug("onPause")
        super.onPause()
    }

    override fun onStop() {
        log.debug("onStop")
        super.onStop()
    }

    override fun onResume() {
        log.debug("onResume")
        super.onResume()
    }

    override fun onStart() {
        log.debug("onStart")
        super.onStart()
    }
}