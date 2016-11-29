package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import org.slf4j.LoggerFactory

class RegistrationOneFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.registration_one_fragment, container, false)

        val submitStepOne = v?.findViewById(R.id.submit_step_one) as Button
        submitStepOne.setOnClickListener {
            handleSubmitStepOne()
        }

        val loginLink = v?.findViewById(R.id.registration_login_link) as TextView
        loginLink.setOnClickListener {
            val fragment = LoginFragment()
            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()
            fragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack("registration").commit()
        }

        return v
    }

    private fun handleSubmitStepOne () {
        val nameField = v?.findViewById(R.id.registration_name) as EditText

        val name = nameField.text.toString()
        var valid = true

        if (name.isEmpty()) {
            nameField.error = "Your name is required"
            valid = false
        }

        if (valid) {
            val mainAtivity = activity as MainActivity
            mainAtivity.registrationInfo.name = name
            goToStepTwo()
        }
    }

    private fun goToStepTwo() {
        val fragment = RegistrationTwoFragment()
        fragment.view?.isFocusableInTouchMode = true
        fragment.view?.requestFocus()

        fragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack("registrationStepOne").commit()
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