package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.AppCompatButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.services.ResetAccountService
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.ui.failUi
import org.slf4j.LoggerFactory

class StartResetRequestFragment: Fragment() {
    companion object {
        fun getNewInstance(): Fragment {
            val fragment = StartResetRequestFragment()
            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()

            return fragment
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp

    private lateinit var resetAccountService: ResetAccountService

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.start_reset_request_fragment, container, false)

        app = AndroidApp.get(activity)
        resetAccountService = app.appComponent.resetAccountService

        val loginLink = v?.findViewById(R.id.back_to_login) as TextView
        loginLink.setOnClickListener {
            activity.finish()
        }

        val submitRequest = v?.findViewById(R.id.submit_reset_request) as AppCompatButton
        submitRequest.setOnClickListener {
            handleSubmitRequest()
        }

        return v
    }

    private fun handleSubmitRequest() {
        val mUsername = v?.findViewById(R.id.forgot_password_username) as EditText
        val username = mUsername.text.toString()
        if (username == "") {
            mUsername.error = resources.getString(R.string.forgot_password_username_required_error)
            return
        }

        resetAccountService.resetAccount(username) mapUi {
            val phoneFreed = it.phoneNumberIsReleased
            val emailFreed = it.emailIsReleased
            if (it.isSuccess && phoneFreed !== null && emailFreed !== null) {
                loadConfirmFragment(username, phoneFreed, emailFreed)
            }
            else {
                mUsername.error = it.errorMessage
            }
        } failUi {
            mUsername.error = resources.getString(R.string.registration_global_error)
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }

    private fun loadConfirmFragment(username: String, phoneFreed: Boolean, emailFreed: Boolean) {
        val fragment = ConfirmResetRequestFragment.getNewInstance(username, phoneFreed, emailFreed)
        fragmentManager.beginTransaction().replace(R.id.forgot_password_frag_container, fragment).addToBackStack(ForgotPasswordActivity.START_REQUEST_FRAGMENT).commit()
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