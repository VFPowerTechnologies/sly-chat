package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import io.slychat.messenger.services.RegistrationService
import io.slychat.messenger.services.ui.UIRegistrationInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import java.util.*

class RegistrationFourFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var registrationService : RegistrationService
    private lateinit var mainActivity: MainActivity
    private val phoneUtil = PhoneNumberUtil.getInstance()

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

        populateCountrySelect()

        return v
    }

    private fun populateCountrySelect() {
        val countryList = phoneUtil.supportedRegions.sortedBy { it }.toMutableList()
        val position = countryList.indexOf(Locale.getDefault().country)
        val spinner = v?.findViewById(R.id.registration_country_spinner) as Spinner
        val countryAdapter = ArrayAdapter<String>(mainActivity, android.R.layout.simple_spinner_item, countryList)
        spinner.adapter = countryAdapter
        spinner.setSelection(position)
    }

    private fun handleSubmitStepFour() {
        val mPhone = v?.findViewById(R.id.registration_phone_number) as EditText
        val mCountrySpinner = v?.findViewById(R.id.registration_country_spinner) as Spinner
        val country = mCountrySpinner.selectedItem as String
        val phoneInput = mPhone.text.toString()
        val parsed : Phonenumber.PhoneNumber

        if(phoneInput.isEmpty()) {
            displayError("Phone number is required")
            return
        }

        try {
            parsed = phoneUtil.parse(phoneInput, country)
        } catch (e: Exception) {
            displayError("Phone does not seem to be valid")
            return
        }

        if (!phoneUtil.isValidNumberForRegion(parsed, country)) {
            displayError("Phone does not seem to be valid")
            return
        }

        val phone = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)

        checkPhoneAvailability(phone.replace("+", ""))
    }

    private fun displayError(error: String) {
        val mPhone = v?.findViewById(R.id.registration_phone_number) as EditText
        mPhone.error = error
    }

    private fun checkPhoneAvailability(phone: String) {
        val mainActivity = activity as MainActivity

        mainActivity.showProgressDialog("Checking for phone number availability")
        registrationService.checkPhoneNumberAvailability(phone) successUi { available ->
            if(available) {
                mainActivity.registrationInfo.phoneNumber = phone
                mainActivity.setProgressDialogMessage("Registration in progress")
                doRegistration()

            }
            else {
                mainActivity.hideProgressDialog()
                displayError("Phone number is already in use")
            }
        } failUi {
            mainActivity.hideProgressDialog()
            displayError("An error occurred")
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