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
import io.slychat.messenger.android.R
import org.slf4j.LoggerFactory

class RegistrationOneFragment: Fragment() {
    companion object {
        fun getNewInstance(): Fragment {
            val fragment = RegistrationOneFragment()
            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()

            return fragment
        }
    }

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
            startActivity(Intent(activity, LoginActivity::class.java))
            activity.finish()
        }

        return v
    }

    private fun handleSubmitStepOne () {
        val nameField = v?.findViewById(R.id.registration_name) as EditText

        val name = nameField.text.toString()
        var valid = true

        if (name.isEmpty()) {
            nameField.error = resources.getString(R.string.registration_name_required_error)
            valid = false
        }

        if (valid) {
            val registrationActivity = activity as RegistrationActivity
            registrationActivity.registrationInfo.name = name
            goToStepTwo()
        }
    }

    private fun goToStepTwo() {
        val fragment = RegistrationTwoFragment.getNewInstance()
        fragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack(RegistrationOneFragment::class.java.name).commit()
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