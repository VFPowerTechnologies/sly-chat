package io.slychat.messenger.android.activites

import android.content.Intent
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
import io.slychat.messenger.services.ui.UIUpdatePhoneInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class UpdatePhoneFragment: Fragment() {
    companion object {
        val EXTRA_USERNAME = "io.slychat.messenger.android.activities.UpdatePhoneFragment.username"
        val EXTRA_PASSWORD = "io.slychat.messenger.android.activities.UpdatePhoneFragment.password"

        fun getNewInstance(username: String, password: String): Fragment {
            val fragment = UpdatePhoneFragment()
            val bundle = Bundle()
            bundle.putString(EXTRA_USERNAME, username)
            bundle.putString(EXTRA_PASSWORD, password)

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

    private lateinit var registrationActivity: RegistrationActivity

    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registrationActivity = activity as RegistrationActivity

        email = this.arguments[EXTRA_USERNAME] as String
        password = this.arguments[EXTRA_PASSWORD] as String
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View ? {
        v = inflater?.inflate(R.layout.update_phone_fragment, container, false)
        app = AndroidApp.get(activity)

        val updateSubmitbtn = v?.findViewById(R.id.update_phone_submit_btn) as Button
        val cancelLink = v?.findViewById(R.id.update_phone_login_link) as TextView
        val verifyLink = v?.findViewById(R.id.update_phone_verify_link) as TextView

        updateSubmitbtn.setOnClickListener {
            handleSubmitUpdate()
        }

        cancelLink.setOnClickListener {
            activity.finish()
        }

        verifyLink.setOnClickListener {
            registrationActivity.startSmsVerification(UpdatePhoneFragment::class.java.name)
        }

        return v
    }

    private fun handleSubmitUpdate () {
        val phoneField = v?.findViewById(R.id.update_phone_phone_field) as EditText
        val phone = phoneField.text.toString()
        if (phone.isEmpty()) {
            phoneField.error = resources.getString(R.string.registration_phone_required_error)
            return
        }

        registrationActivity.showProgressDialog(resources.getString(R.string.registration_update_phone_process))

        app.appComponent.registrationService.updatePhone(UIUpdatePhoneInfo(email, password, phone)) successUi { result ->
            registrationActivity.hideProgressDialog()
            if (result.successful) {
                registrationActivity.startSmsVerification(UpdatePhoneFragment::class.java.name)
            }
            else {
                log.debug(result.errorMessage)
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