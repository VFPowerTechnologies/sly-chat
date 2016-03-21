package com.vfpowertech.keytap.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.widget.TextView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller
import org.slf4j.LoggerFactory

class SplashActivity : Activity() {
    private val log = LoggerFactory.getLogger(javaClass)

    private var progressText: TextView? = null

    //set to false when the ssl provider check completes, or a failure occurs
    private var isCheckRunning = false

    private var isActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        log.info("Activity created")
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.activity_splash)

        progressText = findViewById(R.id.progressText) as TextView
    }

    private fun startChecks() {
        if (isCheckRunning)
            return

        isCheckRunning = true

        updateProgressText("Checking for Play Services...")

        if (!checkPlayServices()) {
            log.info("No play services")
        }
        else {
            log.info("Play services available")
            installSSLProvider()
        }
    }

    private fun updateProgressText(what: String) {
        progressText!!.text = what
    }

    private fun installSSLProvider() {
        log.info("Installing SSL Provider")
        updateProgressText("Checking for GMS SSL Provider...")

        ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
            override fun onProviderInstalled() {
                isCheckRunning = false

                log.info("GMS Provider installed")
                updateProgressText("Checking for GMS SSL Provider... OK")
                startMainActivity()
            }

            override fun onProviderInstallFailed(i: Int, intent: Intent) {
                isCheckRunning = false

                log.error("Provider installation failed: " + i)
                updateProgressText("Checking for GMS SSL Provider... FAILED")

                val dialog = GoogleApiAvailability.getInstance().getErrorDialog(this@SplashActivity, i, 0)
                dialog.setOnDismissListener {
                    finish()
                }
                dialog.show()
            }
        })
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()

        isActive = false
    }

    override fun onResume() {
        super.onResume()

        isActive = true

        startChecks()
    }

    private fun checkPlayServices(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()

        val resultCode = apiAvailability.isGooglePlayServicesAvailable(this)

        if (resultCode == ConnectionResult.SUCCESS)
            return true

        isCheckRunning = false

        if (apiAvailability.isUserResolvableError(resultCode)) {
            val dialog = apiAvailability.getErrorDialog(this, resultCode, 0)

            dialog.setOnDismissListener {
                //for certain errors, the dialog is just closeable (eg: corrupt play services install)
                //if the dialog opens any other activity, we're put in the background so do nothing
                //otherwise, close the app since the user took no action to resolve the issue
                if (isActive)
                    finish()
            }

            dialog.setOnCancelListener {
                //if the user cancels the dialog we can't continue (since nothing's changed)
                finish()
            }

            dialog.show()
        }
        else {
            log.error("Unsupported device")
        }

        return false
    }
}
