package io.slychat.messenger.android.activites

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.Toolbar
import android.util.SparseArray
import android.view.MenuItem
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.SettingsServiceImpl
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import java.util.Arrays

class SettingsActivity: BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val RINGTONE_PICKER_REQUEST_CODE = 1
    }

    private lateinit var app: AndroidApp
    private lateinit var settingsService: SettingsServiceImpl

    private var nextPermRequestCode = 0
    private val permRequestCodeToDeferred = SparseArray<Deferred<Boolean, Exception>>()

    private lateinit var notificationSwitch: Switch
    private lateinit var notificationSoundName: TextView
    private lateinit var chooseNotification: LinearLayout
    private lateinit var darkThemeSwitch: Switch
    private lateinit var inviteSwitch: Switch

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_settings)

        val actionBar = findViewById(R.id.setting_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.settings_title)
        setSupportActionBar(actionBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settingsService = SettingsServiceImpl(this)

        notificationSwitch = findViewById(R.id.settings_notification_switch) as Switch
        notificationSoundName = findViewById(R.id.settings_notification_sound_name) as TextView
        notificationSwitch.isChecked = settingsService.notificationEnabled
        notificationSoundName.text = settingsService.notificationConfig.soundName

        chooseNotification = findViewById(R.id.settings_choose_notification) as LinearLayout
        darkThemeSwitch = findViewById(R.id.settings_dark_theme_switch) as Switch
        if (settingsService.selectedTheme == SettingsServiceImpl.darkTheme)
            darkThemeSwitch.isChecked = true
        else
            darkThemeSwitch.isChecked = false

        inviteSwitch = findViewById(R.id.settings_invite_switch) as Switch
        inviteSwitch.isChecked = settingsService.marketingShowInviteFriends

        createEventListeners()
        setConfigListeners()

        app = AndroidApp.get(this)
    }

    private fun setConfigListeners () {
        settingsService.addNotificationConfigListener {
            notificationSoundName.text = it.soundName
        }

        settingsService.addAppearanceConfigListener {
            if (it.theme == SettingsServiceImpl.lightTheme)
                setTheme(R.style.SlyThemeLight)
            else
                setTheme(R.style.SlyTheme)

            finishAffinity()
            val settingsIntent = Intent(baseContext, SettingsActivity::class.java)
            val recentChatIntent = Intent(baseContext, RecentChatActivity::class.java)
            startActivities(arrayOf(recentChatIntent, settingsIntent))
        }
    }

    private fun clearConfigListeners () {
        settingsService.clearConfigListener()
    }

    private fun createEventListeners () {
        chooseNotification.setOnClickListener {
            handleNotificationChooser()
        }

        notificationSwitch.setOnCheckedChangeListener { compoundButton, b ->
            settingsService.notificationEnabled = b
        }

        darkThemeSwitch.setOnCheckedChangeListener { compoundButton, b ->
            if (b)
                settingsService.selectedTheme = SettingsServiceImpl.darkTheme
            else
                settingsService.selectedTheme = SettingsServiceImpl.lightTheme
        }

        inviteSwitch.setOnCheckedChangeListener { compoundButton, b ->
            settingsService.marketingShowInviteFriends = b
        }
    }

    private fun handleNotificationChooser () {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            openRingtonePicker(settingsService.notificationConfig.sound)
        }
        else {
            requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE) map { granted ->
                if (granted)
                    openRingtonePicker(settingsService.notificationConfig.sound)
            }
        }
    }

    private fun openRingtonePicker (previous: String?) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)

        val previousRingtoneUri = previous?.let { Uri.parse(it) }

        intent.apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, resources.getString(R.string.settings_ringtone_intent_text))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)

            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, previousRingtoneUri)
        }

        startActivityForResult(intent, SettingsActivity.RINGTONE_PICKER_REQUEST_CODE)
    }

    override fun onActivityResult (requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RINGTONE_PICKER_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    log.debug("Ringtone picker failed")
                    return
                }

                //should never occur
                if (data == null) {
                    log.error("No data returned for ringtone picker")
                    return
                }

                val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

                val value = uri?.toString()
                if (value != null)
                    settingsService.notificationSound = value
            }

            else -> {
                log.error("Unknown request code: {}", requestCode)
            }
        }
    }

//    private fun requestPermission(permission: String): Promise<Boolean, Exception> {
//        val requestCode = nextPermRequestCode
//        nextPermRequestCode += 1
//
//        val deferred = deferred<Boolean, Exception>()
//        permRequestCodeToDeferred.put(requestCode, deferred)
//
//        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
//
//        return deferred.promise
//    }

//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        val deferred = permRequestCodeToDeferred[requestCode]
//
//        if (deferred == null) {
//            log.error("Got response for unknown request code ({}); permissions={}", requestCode, Arrays.toString(permissions))
//            return
//        }
//
//        permRequestCodeToDeferred.remove(requestCode)
//
//        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
//
//        deferred.resolve(granted)
//    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    fun setAppActivity() {
        app.setCurrentActivity(this, true)
    }

    fun clearAppActivity() {
        app.setCurrentActivity(this, false)
    }

    override fun onStart () {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause () {
        super.onPause()
        clearAppActivity()
        log.debug("onPause")
    }

    override fun onResume () {
        super.onResume()
        setAppActivity()
        log.debug("onResume")
    }

    override fun onStop () {
        super.onStop()
        clearConfigListeners()
        log.debug("onStop")
    }

    override fun onDestroy () {
        super.onDestroy()
        clearAppActivity()
        log.debug("onDestroy")
    }
}