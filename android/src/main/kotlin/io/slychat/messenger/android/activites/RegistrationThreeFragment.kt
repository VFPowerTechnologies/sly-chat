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
import org.slf4j.LoggerFactory

class RegistrationThreeFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var registrationService : RegistrationService
    private lateinit var app: AndroidApp
    private lateinit var registrationActivity: RegistrationActivity

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.registration_three_fragment, container, false)
        app = AndroidApp.get(activity)
        registrationActivity = activity as RegistrationActivity
        registrationService = app.appComponent.registrationService

        val submitStepThree = v?.findViewById(R.id.submit_step_three) as Button
        submitStepThree.setOnClickListener {
            handleSubmitStepThree()
        }

        return v
    }

    private fun handleSubmitStepThree() {
        val mPassword = v?.findViewById(R.id.registration_password) as EditText
        val mPasswordConf = v?.findViewById(R.id.registration_password_confirmation) as EditText

        val password = mPassword.text.toString()
        val passwordConf = mPasswordConf.text.toString()
        var valid = true

        if(password.isEmpty()) {
            mPassword.error = resources.getString(R.string.registration_password_required_error)
            valid = false
        }
        else if(password != passwordConf) {
            mPasswordConf.error = resources.getString(R.string.registration_password_match_error)
            valid = false
        }

        if (valid) {
            registrationActivity.registrationInfo.password = password
            goToStepFour()
        }
    }

    private fun goToStepFour() {
        val fragment = RegistrationFourFragment()
        fragment.view?.isFocusableInTouchMode = true
        fragment.view?.requestFocus()

        fragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack("registrationStepThree").commit()
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