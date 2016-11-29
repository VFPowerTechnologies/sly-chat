package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import io.slychat.messenger.services.ui.UIUpdatePhoneInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class UpdatePhoneFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)
    private var v: View? = null

    private lateinit var email: String
    private lateinit var password: String

    private lateinit var mainActivity: MainActivity

    private lateinit var app: AndroidApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        email = this.arguments["EXTRA_EMAIL"] as String
        password = this.arguments["EXTRA_PASSWORD"] as String
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View ? {
        v = inflater?.inflate(R.layout.update_phone_fragment, container, false)
        app = AndroidApp.get(activity)
        mainActivity = activity as MainActivity

        val updateSubmitbtn = v?.findViewById(R.id.update_phone_submit_btn) as Button
        val cancelLink = v?.findViewById(R.id.update_phone_login_link) as TextView
        val verifyLink = v?.findViewById(R.id.update_phone_verify_link) as TextView

        updateSubmitbtn.setOnClickListener {
            handleSubmitUpdate()
        }

        cancelLink.setOnClickListener {
            fragmentManager.popBackStack("login", FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        verifyLink.setOnClickListener {
            mainActivity.startSmsVerification("updatePhone")
        }

        return v
    }

    private fun handleSubmitUpdate () {
        val phoneField = v?.findViewById(R.id.update_phone_phone_field) as EditText
        val phone = phoneField.text.toString()
        if (phone.isEmpty()) {
            phoneField.error = "Phone is required"
            return
        }

        mainActivity.showProgressDialog("Updating Phone")

        app.appComponent.registrationService.updatePhone(UIUpdatePhoneInfo(email, password, phone)) successUi { result ->
            mainActivity.hideProgressDialog()
            if (result.successful) {
                mainActivity.startSmsVerification("updatePhone")
            }
            else {
                log.debug(result.errorMessage)
            }
        } failUi {
            mainActivity.hideProgressDialog()
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