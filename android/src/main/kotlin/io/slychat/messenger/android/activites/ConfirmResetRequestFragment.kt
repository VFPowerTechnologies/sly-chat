package io.slychat.messenger.android.activites


import android.app.AlertDialog
import android.content.DialogInterface
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
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

class ConfirmResetRequestFragment: Fragment() {
    companion object {
        fun getNewInstance(username: String, phone: Boolean, email: Boolean): Fragment {
            val fragment = ConfirmResetRequestFragment()
            fragment.view?.isFocusableInTouchMode = true
            fragment.view?.requestFocus()
            val bundle = Bundle()
            bundle.putString(ForgotPasswordActivity.EXTRA_USERNAME, username)
            bundle.putBoolean(ForgotPasswordActivity.EXTRA_PHONE_FREED, phone)
            bundle.putBoolean(ForgotPasswordActivity.EXTRA_EMAIL_FREED, email)
            fragment.arguments = bundle

            return fragment
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp
    private lateinit var resetAccountService: ResetAccountService
    private lateinit var forgotPasswordActivity: ForgotPasswordActivity

    private var v: View? = null

    private lateinit var username: String
    private var emailFreed = false
    private var phoneFreed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater?.inflate(R.layout.confirm_reset_request_fragment, container, false)

        app = AndroidApp.get(activity)
        resetAccountService = app.appComponent.resetAccountService
        forgotPasswordActivity = activity as ForgotPasswordActivity

        username = this.arguments[ForgotPasswordActivity.EXTRA_USERNAME] as String
        emailFreed = this.arguments[ForgotPasswordActivity.EXTRA_EMAIL_FREED] as Boolean
        phoneFreed = this.arguments[ForgotPasswordActivity.EXTRA_PHONE_FREED] as Boolean

        val mLoginLink = v?.findViewById(R.id.back_to_login) as TextView
        mLoginLink.setOnClickListener {
            activity.finish()
        }

        val mSubmitEmailCode = v?.findViewById(R.id.forgot_password_submit_email_code) as AppCompatButton
        mSubmitEmailCode.setOnClickListener {
            handleEmailSubmitCode()
        }

        val mSubmitSmsCode = v?.findViewById(R.id.forgot_password_submit_sms_code) as AppCompatButton
        mSubmitSmsCode.setOnClickListener {
            handleSmsSubmitCode()
        }

        return v
    }

    private fun handleEmailSubmitCode() {
        val mEmailCode = v?.findViewById(R.id.forgot_password_email_code) as EditText
        val code = mEmailCode.text.toString()
        if (code == "") {
            mEmailCode.error = resources.getString(R.string.forgot_password_email_code_required_error)
            return
        }

        resetAccountService.submitEmailResetCode(username, code) successUi {
            if (it.isSuccess) {
                emailSuccess()
            }
            else {
                mEmailCode.error = it.errorMessage
            }
        } failUi {
            mEmailCode.error = resources.getString(R.string.registration_global_error)
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }

    private fun handleSmsSubmitCode() {
        val mSmsCode = v?.findViewById(R.id.forgot_password_sms_code) as EditText
        val code = mSmsCode.text.toString()
        if (code == "") {
            mSmsCode.error = resources.getString(R.string.forgot_password_email_code_required_error)
            return
        }

        resetAccountService.submitSmsResetCode(username, code) successUi {
            if (it.isSuccess) {
                smsSuccess()
            }
            else {
                mSmsCode.error = it.errorMessage
            }
        } failUi {
            mSmsCode.error = resources.getString(R.string.registration_global_error)
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }

    private fun smsSuccess() {
        phoneFreed = true
        openDialog(resources.getString(R.string.global_success), resources.getString(R.string.forgot_password_phone_success_text), { finishResetProcedure() })
        v?.findViewById(R.id.forgot_password_phone_verification)?.visibility = View.GONE
    }

    private fun emailSuccess() {
        emailFreed = true
        openDialog(resources.getString(R.string.global_success), resources.getString(R.string.forgot_password_email_success_text), { finishResetProcedure() })
        v?.findViewById(R.id.forgot_password_email_verification)?.visibility = View.GONE

    }

    private fun finishResetProcedure() {
        if (emailFreed && phoneFreed) {
            openDialog(resources.getString(R.string.global_success), resources.getString(R.string.forgot_password_request_complete_text), { activity.finish() })
        }
    }

    private fun openDialog(title: String, message: String, callback: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        dialog?.dismiss()
                        callback()
                    }
                })
                .create()

        dialog.show()
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