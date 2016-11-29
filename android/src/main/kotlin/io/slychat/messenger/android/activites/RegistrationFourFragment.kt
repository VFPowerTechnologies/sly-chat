package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import io.slychat.messenger.services.RegistrationService
import io.slychat.messenger.services.ui.UIRegistrationInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class RegistrationFourFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var registrationService : RegistrationService
    private lateinit var mainActivity: MainActivity

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.registration_four_fragment, container, false)
        val app = AndroidApp.get(activity)
        mainActivity = activity as MainActivity
        registrationService = app.appComponent.registrationService

        mainActivity.setRegistrationListener()

        val submitStepFour = v?.findViewById(R.id.submit_step_four) as Button
        submitStepFour.setOnClickListener {
            handleSubmitStepFour()
        }

        return v
    }

    private fun handleSubmitStepFour() {
        val mPhone = v?.findViewById(R.id.registration_phone_number) as EditText

        val phone = mPhone.text.toString()
        var valid = true

        if(phone.isEmpty()) {
            mPhone.error = "Phone number is required"
            valid = false
        }

        if (valid) {
            checkPhoneAvailability(phone)
        }
    }

    private fun checkPhoneAvailability(phone: String) {
        val mainActivity = activity as MainActivity
        val mPhone = v?.findViewById(R.id.registration_phone_number) as EditText

        mainActivity.showProgressDialog("Checking for phone number availability")
        registrationService.checkPhoneNumberAvailability(phone) successUi { available ->
            if(available) {
                mainActivity.registrationInfo.phoneNumber = phone
                mainActivity.setProgressDialogMessage("Registration in progress")
                doRegistration()

            }
            else {
                mainActivity.hideProgressDialog()
                mPhone.error = "Email is already in use"
            }
        } failUi {
            mainActivity.hideProgressDialog()
            mPhone.error = "An error occurred"
        }
    }

    private fun doRegistration() {
        val info = mainActivity.registrationInfo
        val uiRegistrationInfo = UIRegistrationInfo(info.name, info.email, info.password, info.phoneNumber)

        registrationService.doRegistration(uiRegistrationInfo)
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