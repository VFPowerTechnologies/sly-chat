package io.slychat.messenger.android.activites

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.SparseArray
import android.view.WindowManager
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.SettingsServiceImpl
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.PageType
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.slf4j.LoggerFactory
import java.util.*

open class BaseActivity : AppCompatActivity() {
    private var nextPermRequestCode = 0
    private val permRequestCodeToDeferred = SparseArray<Deferred<Boolean, Exception>>()

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onCreate(savedInstanceState: Bundle?) {
        log.debug("onCreate")
        val app = getAndroidApp()
        if (app !== null) {
            val currentTheme = app.appComponent.appConfigService.appearanceTheme

            if (currentTheme == SettingsServiceImpl.lightTheme) {
                setTheme(R.style.SlyThemeLight)
            }
        }
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun getAndroidApp(): AndroidApp? {
        return if (this !is MainActivity)
            AndroidApp.get(this)
        else
            null
    }

    fun requestPermission(permission: String): Promise<Boolean, Exception> {
        val requestCode = nextPermRequestCode
        nextPermRequestCode += 1

        val deferred = deferred<Boolean, Exception>()
        permRequestCodeToDeferred.put(requestCode, deferred)

        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)

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

    fun getChatPageIntent(conversationId: ConversationId): Intent {
        val intent = Intent(baseContext, ChatActivity::class.java)

        if (conversationId is ConversationId.User) {
            intent.putExtra("EXTRA_ISGROUP", false)
            intent.putExtra("EXTRA_ID", conversationId.id.long)
        }
        else if (conversationId is ConversationId.Group) {
            intent.putExtra("EXTRA_ISGROUP", true)
            intent.putExtra("EXTRA_ID", conversationId.id.string)
        }

        return intent
    }

    fun setAppActivity() {
        val app = getAndroidApp()
        if (app !== null)
            app.setCurrentActivity(this, true)
    }

    fun clearAppActivity() {
        val app = getAndroidApp()
        if (app !== null)
            app.setCurrentActivity(this, false)
    }

    private fun dispatchEvent() {
        val app = getAndroidApp() ?: return

        val pageType: PageType
        var extra = ""
        val eventType = "PageChange"

        when (this) {
            is RecentChatActivity -> {
                pageType = PageType.CONTACTS
            }
            is ChatActivity -> {
                val cId = this.conversationId
                if (cId is ConversationId.User) {
                    pageType = PageType.CONVO
                    extra = cId.id.long.toString()
                }
                else if (cId is ConversationId.Group) {
                    pageType = PageType.GROUP
                    extra = cId.id.string
                }
                else
                    pageType = PageType.OTHER
            }
            else -> {
                pageType = PageType.OTHER
            }
        }

        app.dispatchEvent(eventType, pageType, extra)
    }

    fun pxToDp(sizeInDp: Int): Int {
        val scale = resources.displayMetrics.density
        return (sizeInDp*scale + 0.5f).toInt()
    }

    override fun onRestart() {
        log.debug("onRestart")
        super.onRestart()
    }

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        log.debug("onPause")
        clearAppActivity()
    }

    override fun onResume() {
        super.onResume()
        setAppActivity()
        dispatchEvent()
        log.debug("onResume")
    }

    override fun onStop() {
        super.onStop()
        clearAppActivity()
        log.debug("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        log.debug("onDestroy")
    }
}