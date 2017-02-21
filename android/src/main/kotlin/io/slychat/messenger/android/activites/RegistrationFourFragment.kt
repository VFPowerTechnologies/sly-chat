package io.slychat.messenger.android.activites

import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.services.RegistrationService
import io.slychat.messenger.services.ui.UIRegistrationInfo
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import java.util.*

class RegistrationFourFragment: Fragment() {
    companion object {
        fun getNewInstance(): Fragment {
            val fragment = RegistrationFourFragment()
            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()

            return fragment
        }
    }
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var registrationService : RegistrationService
    private lateinit var registrationActivity: RegistrationActivity
    private val phoneUtil = PhoneNumberUtil.getInstance()

    private lateinit var app: AndroidApp

    private val permRequestCodeToDeferred = SparseArray<Deferred<Boolean, Exception>>()

    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.registration_four_fragment, container, false)
        app = AndroidApp.get(activity)
        registrationService = app.appComponent.registrationService
        registrationActivity = activity as RegistrationActivity

        val submitStepFour = v?.findViewById(R.id.submit_step_four) as Button
        submitStepFour.setOnClickListener {
            handleSubmitStepFour()
        }

        populateCountrySelect()
        displayPhoneNumber()

        return v
    }

    private fun populateCountrySelect() {
        val countryList = phoneUtil.supportedRegions.sortedBy { it }.toMutableList()
        val position = countryList.indexOf(Locale.getDefault().country)
        val spinner = v?.findViewById(R.id.registration_country_spinner) as Spinner
        val countryAdapter = ArrayAdapter<String>(registrationActivity, android.R.layout.simple_spinner_item, countryList)
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
            displayError(resources.getString(R.string.registration_phone_required_error))
            return
        }

        try {
            parsed = phoneUtil.parse(phoneInput, country)
        } catch (e: Exception) {
            displayError(resources.getString(R.string.registration_phone_invalid_error))
            return
        }

        if (!phoneUtil.isValidNumberForRegion(parsed, country)) {
            displayError(resources.getString(R.string.registration_phone_invalid_error))
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
        registrationActivity.showProgressDialog(resources.getString(R.string.registration_phone_verification_process))
        registrationService.checkPhoneNumberAvailability(phone) successUi { available ->
            if(available) {
                registrationActivity.registrationInfo.phoneNumber = phone
                registrationActivity.setProgressDialogMessage(resources.getString(R.string.registration_process))
                doRegistration()

            }
            else {
                registrationActivity.hideProgressDialog()
                displayError(resources.getString(R.string.registration_phone_taken_error))
            }
        } failUi {
            registrationActivity.hideProgressDialog()
            displayError(resources.getString(R.string.registration_global_error))
        }
    }

    private fun doRegistration() {
        val info = registrationActivity.registrationInfo
        val uiRegistrationInfo = UIRegistrationInfo(info.name, info.email, info.password, info.phoneNumber)

        registrationService.doRegistration(uiRegistrationInfo)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val deferred = permRequestCodeToDeferred[requestCode]

        if (deferred == null) {
            log.error("Got response for unknown request code ({}); permissions={}", requestCode, Arrays.toString(permissions))
            return
        }

        permRequestCodeToDeferred.remove(requestCode)

        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

        deferred.resolve(granted)
    }

    private fun displayPhoneNumber() {
        app.appComponent.uiTelephonyService.getDevicePhoneNumber() successUi { phone ->
            setDefaultPhoneNumber(phone)
        } failUi {
            log.error("Something failed ${it.message}", it)
        }
    }

    private fun setDefaultPhoneNumber(phone: String?) {
        if (phone !== null) {
            val mPhone = v?.findViewById(R.id.registration_phone_number) as EditText
            // For some reason using setText() creates an indexOutOfRange exception
            // Using clear than append instead works
            mPhone.text.clear()
            mPhone.append(phone)
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