package io.slychat.messenger.android.activites

import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.WindowManager
import android.widget.*
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidAccountServiceImpl
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.AccountInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import java.util.*

class UpdateProfileActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var accountService: AndroidAccountServiceImpl

    private val phoneUtil = PhoneNumberUtil.getInstance()

    private lateinit var app: AndroidApp

    private lateinit var accountInfo: AccountInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_update_profile)

        val actionBar = findViewById(R.id.update_profile_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.update_profile_title)
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        app = AndroidApp.get(this)

        accountService = AndroidAccountServiceImpl(this)
        setEventListener()
    }

    private fun init() {
        accountInfo = accountService.getAccountInfo()
        displayInfo()
        populateCountrySelect()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setEventListener() {
        val updatePhoneBtn = findViewById(R.id.update_profile_phone_button) as Button
        val updateInfoBtn = findViewById(R.id.update_profile_info_button) as Button

        updateInfoBtn.setOnClickListener {
            updateProfileInfo()
        }

        updatePhoneBtn.setOnClickListener {
            log.debug("click listener")
            handlePhoneUpdate()
        }
    }

    private fun updateProfileInfo() {
        val mName = findViewById(R.id.update_profile_name_input) as EditText
        val mEmail = findViewById(R.id.update_profile_email_input) as EditText
        val error = findViewById(R.id.update_profile_info_error) as TextView
        val newName = mName.text.toString()
        var newEmail: String? = mEmail.text.toString()
        var valid = true

        if (newName.isEmpty() || newName == "") {
            mName.error = resources.getString(R.string.registration_name_required_error)
            valid = false
        }

        if (newEmail != null && (newEmail.isEmpty() || newEmail == "")) {
            mEmail.error = resources.getString(R.string.registration_email_required_error)
            valid = false
        }

        if (newName == accountInfo.name && newEmail == accountInfo.email)
            valid = false

        if (!valid)
            return

        if (newEmail == accountInfo.email)
            newEmail = null

        accountService.updateInfo(newName, newEmail) successUi { result ->
            if (result.successful) {
                finish()
            }
            else {
                val message = result.errorMessage
                if (message != null)
                    error.text = message
            }
        } failUi {
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }

    }

    private fun handlePhoneUpdate() {
        val mPhone = findViewById(R.id.update_profile_phone_input) as EditText
        val mCountry = findViewById(R.id.update_profile_country_spinner) as Spinner
        val country = mCountry.selectedItem as String
        val phoneInput = mPhone.text.toString()
        val parsed : Phonenumber.PhoneNumber
        val currentPhone = accountInfo.phoneNumber

        if(phoneInput.isEmpty()) {
            mPhone.error = resources.getString(R.string.registration_phone_required_error)
            return
        }

        try {
            parsed = phoneUtil.parse(phoneInput, country)
        } catch (e: Exception) {
            mPhone.error = resources.getString(R.string.registration_phone_invalid_error)
            return
        }

        if (!phoneUtil.isValidNumberForRegion(parsed, country)) {
            mPhone.error = resources.getString(R.string.registration_phone_invalid_error)
            return
        }

        val phone = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        val formattedPhone = phone.replace("+", "")

        if (formattedPhone == currentPhone) {
            log.debug(resources.getString(R.string.registration_phone_identical_error))
            return
        }

        checkPhoneAvailability(formattedPhone)
    }

    private fun updatePhone(phone: String) {
        accountService.updatePhone(phone) successUi { result ->
            if (result.successful) {
                openSmsVerificationModal(null)
            }
            else {
                log.debug(result.errorMessage)
            }
        }
    }

    private fun openSmsVerificationModal(error: String?) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_sms_verification, null)
        val mSmsCode = view.findViewById(R.id.sms_verification_code_field) as EditText

        if (error !== null)
            mSmsCode.error = error

        builder.setMessage(resources.getString(R.string.update_profile_sms_verification_message))
                .setTitle(resources.getString(R.string.update_profile_sms_verification_title))
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(resources.getString(R.string.submit_button), DialogInterface.OnClickListener { dialogInterface, i ->
                    val code = mSmsCode.text.toString()
                    accountService.verifyPhone(code) successUi { result ->
                        if (result.successful && result.accountInfo !== null) {
                            updateAccountInfo(result.accountInfo)
                        }
                        else {
                            openSmsVerificationModal(result.errorMessage)
                        }
                    }

                })
                .setNegativeButton(resources.getString(R.string.cancel_button), DialogInterface.OnClickListener { dialogInterface, i ->
                })
        val dialog = builder.create()
        dialog.show()
    }

    private fun updateAccountInfo(newAccountInfo: AccountInfo) {
        accountService.updateAccountInfo(newAccountInfo) successUi {
            finish()
        } failUi {
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }

    private fun checkPhoneAvailability(phone: String) {
        val mPhone = findViewById(R.id.update_profile_phone_input) as EditText

        accountService.checkPhoneNumberAvailability(phone) successUi { available ->
            if(available) {
                updatePhone(phone)
            }
            else {
                mPhone.error = resources.getString(R.string.registration_phone_taken_error)
            }
        } failUi {
            mPhone.error = resources.getString(R.string.registration_global_error)
        }
    }

    private fun populateCountrySelect() {
        val countryList = phoneUtil.supportedRegions.sortedBy { it }.toMutableList()
        val position = countryList.indexOf(Locale.getDefault().country)
        val mCountry = findViewById(R.id.update_profile_country_spinner) as Spinner
        val countryAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, countryList)
        mCountry.adapter = countryAdapter
        mCountry.setSelection(position)
    }

    private fun displayInfo() {
        val phone = accountInfo.phoneNumber
        val name = accountInfo.name
        val email = accountInfo.email

        setPhoneValue(phone)
        setProfileInfo(name, email)
    }

    private fun setPhoneValue(phone: String) {
        val mPhone = findViewById(R.id.update_profile_phone_input) as EditText
        mPhone.text.clear()
        mPhone.append(phone)
    }

    private fun setProfileInfo(name: String, email: String) {
        val mName = findViewById(R.id.update_profile_name_input) as EditText
        val mEmail = findViewById(R.id.update_profile_email_input) as EditText

        mEmail.text.clear()
        mEmail.append(email)

        mName.text.clear()
        mName.append(name)
    }

    override fun onResume() {
        super.onResume()
        init()
    }
}