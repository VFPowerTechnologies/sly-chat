package io.slychat.messenger.android.activites

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.telephony.TelephonyManager
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import io.slychat.messenger.services.RegistrationService
import io.slychat.messenger.services.ui.UIRegistrationInfo
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import java.util.*

class RegistrationFourFragment: Fragment() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var registrationService : RegistrationService
    private lateinit var mainActivity: MainActivity
    private val phoneUtil = PhoneNumberUtil.getInstance()

    private var nextPermRequestCode = 0
    private val permRequestCodeToDeferred = SparseArray<Deferred<Boolean, Exception>>()

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
        displayPhoneNumber()

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
        val mainActivity = activity as MainActivity

        mainActivity.showProgressDialog(resources.getString(R.string.registration_phone_verification_process))
        registrationService.checkPhoneNumberAvailability(phone) successUi { available ->
            if(available) {
                mainActivity.registrationInfo.phoneNumber = phone
                mainActivity.setProgressDialogMessage(resources.getString(R.string.registration_process))
                doRegistration()

            }
            else {
                mainActivity.hideProgressDialog()
                displayError(resources.getString(R.string.registration_phone_taken_error))
            }
        } failUi {
            mainActivity.hideProgressDialog()
            displayError(resources.getString(R.string.registration_global_error))
        }
    }

    private fun doRegistration() {
        val info = mainActivity.registrationInfo
        val uiRegistrationInfo = UIRegistrationInfo(info.name, info.email, info.password, info.phoneNumber)

        registrationService.doRegistration(uiRegistrationInfo)
    }

    fun requestPermission(activity: Activity, permission: String): Promise<Boolean, Exception> {
        val requestCode = nextPermRequestCode
        nextPermRequestCode += 1

        val deferred = deferred<Boolean, Exception>()
        permRequestCodeToDeferred.put(requestCode, deferred)

        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)

        return deferred.promise
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
        val permission = Manifest.permission.READ_PHONE_STATE

        if (ContextCompat.checkSelfPermission(activity, permission) !== PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                showPermissionRequestDetails()
            } else {
                requestPermission(activity, permission) successUi { granted ->
                    if (granted) {
                        setDefaultPhoneNumber(getPhoneNumber())
                    }
                }
            }
        }
        else {
            setDefaultPhoneNumber(getPhoneNumber())
        }
    }

    private fun showPermissionRequestDetails() {
        val alert = AlertDialog.Builder(activity)
        alert.setCancelable(false)
        alert.setTitle(resources.getString(R.string.permission_request_title))
        alert.setMessage(resources.getString(R.string.permission_request_title_phone_access))
        alert.setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener { dialogInterface, i ->
            requestPermission(activity, Manifest.permission.READ_PHONE_STATE) successUi { granted ->
                if (granted) {
                    setDefaultPhoneNumber(getPhoneNumber())
                }
            }
        })

        alert.show()
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

    private fun getPhoneNumber(): String? {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val phoneNumber = telephonyManager.line1Number
        if (phoneNumber == null || phoneNumber.isEmpty())
            return null
        return if (phoneNumber.startsWith("+"))
            phoneNumber.substring(1)
        else
            phoneNumber
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