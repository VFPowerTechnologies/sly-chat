package io.slychat.messenger.android.activites

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidConfigServiceImpl
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory

class SettingsActivity : BaseActivity() {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val RINGTONE_PICKER_REQUEST_CODE = 1
    }

    private lateinit var app: AndroidApp
    private lateinit var configService: AndroidConfigServiceImpl

    private lateinit var notificationSwitch: Switch
    private lateinit var notificationSoundName: TextView
    private lateinit var chooseNotification: LinearLayout
    private lateinit var darkThemeSwitch: Switch
    private lateinit var inviteSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_settings)

        val actionBar = findViewById(R.id.setting_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.settings_title)
        setSupportActionBar(actionBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        configService = AndroidConfigServiceImpl(this)

        notificationSwitch = findViewById(R.id.settings_notification_switch) as Switch
        notificationSoundName = findViewById(R.id.settings_notification_sound_name) as TextView
        notificationSwitch.isChecked = configService.notificationEnabled
        notificationSoundName.text = configService.notificationConfig.soundName

        chooseNotification = findViewById(R.id.settings_choose_notification) as LinearLayout
        darkThemeSwitch = findViewById(R.id.settings_dark_theme_switch) as Switch
        if (configService.selectedTheme == AndroidConfigServiceImpl.darkTheme)
            darkThemeSwitch.isChecked = true
        else
            darkThemeSwitch.isChecked = false

        inviteSwitch = findViewById(R.id.settings_invite_switch) as Switch
        inviteSwitch.isChecked = configService.marketingShowInviteFriends

        createEventListeners()
        setConfigListeners()

        app = AndroidApp.get(this)
    }

    private fun setConfigListeners() {
        configService.addNotificationConfigListener {
            notificationSoundName.text = it.soundName
        }

        configService.addAppearanceConfigListener {
            if (it.theme == AndroidConfigServiceImpl.lightTheme)
                setTheme(R.style.SlyThemeLight)
            else
                setTheme(R.style.SlyTheme)

            finishAffinity()
            val settingsIntent = Intent(baseContext, SettingsActivity::class.java)
            val recentChatIntent = Intent(baseContext, RecentChatActivity::class.java)
            startActivities(arrayOf(recentChatIntent, settingsIntent))
        }
    }

    private fun clearConfigListeners() {
        configService.clearConfigListener()
    }

    private fun createEventListeners() {
        chooseNotification.setOnClickListener {
            handleNotificationChooser()
        }

        notificationSwitch.setOnCheckedChangeListener { compoundButton, b ->
            configService.notificationEnabled = b
        }

        darkThemeSwitch.setOnCheckedChangeListener { compoundButton, b ->
            if (b)
                configService.selectedTheme = AndroidConfigServiceImpl.darkTheme
            else
                configService.selectedTheme = AndroidConfigServiceImpl.lightTheme
        }

        inviteSwitch.setOnCheckedChangeListener { compoundButton, b ->
            configService.marketingShowInviteFriends = b
        }
    }

    private fun handleNotificationChooser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            openRingtonePicker(configService.notificationConfig.sound)
        }
        else {
            requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE) map { granted ->
                if (granted)
                    openRingtonePicker(configService.notificationConfig.sound)
            }
        }
    }

    private fun openRingtonePicker(previous: String?) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RINGTONE_PICKER_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    log.warn("Ringtone picker failed")
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
                    configService.notificationSound = value
            }

            else -> {
                log.error("Unknown request code: {}", requestCode)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop () {
        super.onStop()
        clearConfigListeners()
    }
}