package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import org.slf4j.LoggerFactory

class LoginFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)
    private var v: View? = null

    private var username: String? = null
    private var password: String? = null

    private lateinit var mainActivity: MainActivity

    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View ? {
        v = inflater?.inflate(R.layout.login_fragment, container, false)
        app = AndroidApp.get(activity)
        mainActivity = activity as MainActivity

        val submitLoginBtn = v?.findViewById(R.id.login_submit_btn) as Button
        submitLoginBtn.setOnClickListener {
            handleSubmitLogin()
        }

        val registerLink = v?.findViewById(R.id.login_register_link) as TextView
        registerLink.setOnClickListener {
            val fragment = RegistrationOneFragment()
            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()
            fragmentManager.beginTransaction().replace(R.id.main_frag_container, fragment).addToBackStack("login").commit()
        }

        return v
    }

    private fun handleSubmitLogin() {
        val usernameField = v?.findViewById(R.id.login_username_field) as EditText
        val passwordField = v?.findViewById(R.id.login_password_field) as EditText
        val rememberMe = v?.findViewById(R.id.login_remember_me) as Switch

        username = usernameField.text.toString()
        password = passwordField.text.toString()
        if (!validate(username, password))
            return

        mainActivity.showProgressDialog(resources.getString(R.string.login_in_process_message))
        mainActivity.registrationInfo.email = username!!
        mainActivity.registrationInfo.password = password!!
        app.app.login(username!!, password!!, rememberMe.isChecked)
    }

    private fun validate (username: String?, password: String?): Boolean {
        var valid = true
        val usernameField = v?.findViewById(R.id.login_username_field) as EditText
        val passwordField = v?.findViewById(R.id.login_password_field) as EditText

        if (username == null || username.isEmpty()) {
            usernameField.error = resources.getString(R.string.login_username_required_error)
            valid = false
        }

        if (password == null || password.isEmpty()) {
            passwordField.error = resources.getString(R.string.login_password_required_error)
            valid = false
        }

        return valid
    }

    fun showLoginError(error: String) {
        val errorView = v?.findViewById(R.id.login_error) as TextView
        errorView.text = error
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