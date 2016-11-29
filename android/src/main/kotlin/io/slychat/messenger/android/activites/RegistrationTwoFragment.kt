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
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import io.slychat.messenger.services.RegistrationService
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class RegistrationTwoFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var registrationService : RegistrationService
    private lateinit var app: AndroidApp
    private lateinit var mainActivity: MainActivity

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.registration_two_fragment, container, false)
        app = AndroidApp.get(activity)
        mainActivity = activity as MainActivity
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
            emailField.error = "Your email is required"
            valid = false
        }

        if (valid) {
            checkAvailability(email)
        }
    }

    private fun checkAvailability(email: String) {
        val mainActivity = activity as MainActivity
        val app = AndroidApp.get(activity)
        val emailField = v?.findViewById(R.id.registration_email) as EditText

        mainActivity.showProgressDialog("Checking for email availability")
        app.appComponent.registrationService.checkEmailAvailability(email) successUi { available ->
            if(available) {
                mainActivity.registrationInfo.email = email
                mainActivity.hideProgressDialog()
                goToStepThree()
            }
            else {
                mainActivity.hideProgressDialog()
                emailField.error = "Email is already in use"
            }
        } failUi {
            mainActivity.hideProgressDialog()
            emailField.error = "An error occurred"
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
        mainActivity.unsubscribeRegistrationListener()
        super.onPause()
    }

    override fun onStop() {
        log.debug("onStop")
        super.onStop()
    }

    override fun onResume() {
        log.debug("onResume")
        mainActivity.setRegistrationListener()
        super.onResume()
    }

    override fun onStart() {
        log.debug("onStart")
        super.onStart()
    }

}