package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.RegistrationService
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class RegistrationTwoFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var registrationService : RegistrationService
    private lateinit var app: AndroidApp
    private lateinit var registrationActivity: RegistrationActivity

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.registration_two_fragment, container, false)
        app = AndroidApp.get(activity)
        registrationActivity = activity as RegistrationActivity
        registrationService = app.appComponent.registrationService

        val submitStepTwo = v?.findViewById(R.id.submit_step_two) as Button
        submitStepTwo.setOnClickListener {
            handleSubmitStepTwo()
        }

        return v
    }

    private fun handleSubmitStepTwo() {
        val emailField = v?.findViewById(R.id.registration_email) as EditText

        val email = emailField.text.toString()
        var valid = true

        if (email.isEmpty()) {
            emailField.error = resources.getString(R.string.registration_email_required_error)
            valid = false
        }

        if (valid) {
            checkAvailability(email)
        }
    }

    private fun checkAvailability(email: String) {
        val app = AndroidApp.get(activity)
        val emailField = v?.findViewById(R.id.registration_email) as EditText

        registrationActivity.showProgressDialog(resources.getString(R.string.registration_email_verification_process))
        app.appComponent.registrationService.checkEmailAvailability(email) successUi { available ->
            if(available) {
                registrationActivity.registrationInfo.email = email
                registrationActivity.hideProgressDialog()
                goToStepThree()
            }
            else {
                registrationActivity.hideProgressDialog()
                emailField.error = resources.getString(R.string.registration_email_taken_error)
            }
        } failUi {
            registrationActivity.hideProgressDialog()
            emailField.error = resources.getString(R.string.registration_global_error)
            log.debug(it.message)
        }
    }

    private fun goToStepThree() {
        val fragment = RegistrationThreeFragment()
        fragment.view?.isFocusableInTouchMode = true
        fragment.view?.requestFocus()

        fragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack("registrationStepTwo").commit()
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