package io.slychat.messenger.android

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import io.slychat.messenger.core.UserId

object AndroidPreferences {
    class EditorInterface(private val editor: SharedPreferences.Editor) {
        fun setTokenUserList(v: Set<String>) = setStringSetProp(editor, tokenUserListKey, v)
        fun setIgnoreNotifications(v: Boolean) = setBoolProp(editor, ignoreNotificationsKey, v)
        fun setTokenSentToServer(userId: UserId, v: Boolean) = setBoolProp(editor, tokenSentToServerKey(userId), v)
    }

    private val tokenUserListKey: String = "tokenUserList"

    private fun tokenSentToServerKey(userId: UserId): String =
        "${userId.long}/tokenSentToServer"

    //this is used to ignore received gcm notifications after the user has requested them to stop
    //it takes some time for the token to die, so sometimes notifications come in after we've deleted the token
    //we currently only allow logged out notifications for a single account
    private val ignoreNotificationsKey: String = "ignoreNotifications"

    fun getTokenUserList(context: Context): Set<String> = getStringSetProp(context, tokenUserListKey, setOf())
    fun setTokenUserList(context: Context, v: Set<String>) = setStringSetProp(context, tokenUserListKey, v)

    fun getTokenSentToServer(context: Context, userId: UserId): Boolean = getBoolProp(context, tokenSentToServerKey(userId), false)
    fun setTokenSentToServer(context: Context, userId: UserId, v: Boolean) = setBoolProp(context, tokenSentToServerKey(userId), v)

    fun getIgnoreNotifications(context: Context): Boolean = getBoolProp(context, ignoreNotificationsKey, false)
    fun setIgnoreNotifications(context: Context, v: Boolean) = setBoolProp(context, ignoreNotificationsKey, v)

    fun getPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private fun getStringSetProp(context: Context, key: String, default: Set<String>): Set<String> =
        getPrefs(context).getStringSet(key, default)

    private fun setStringSetProp(context: Context, key: String, v: Set<String>) =
        getPrefs(context).edit().putStringSet(key, v).apply()

    private fun setStringSetProp(editor: SharedPreferences.Editor, key: String, v: Set<String>) =
        editor.putStringSet(key, v)

    private fun getBoolProp(context: Context, key: String, default: Boolean): Boolean =
        getPrefs(context).getBoolean(key, default)

    private fun setBoolProp(editor: SharedPreferences.Editor, key: String, v: Boolean) {
        editor.putBoolean(key, v)
    }

    private fun setBoolProp(context: Context, key: String, v: Boolean) {
        getPrefs(context).edit().putBoolean(key, v).apply()
    }

    /** Auto applies on successful scope exit. */
    fun <R> withEditor(context: Context, body: EditorInterface.() -> R): R {
        val sharedPrefs = getPrefs(context)
        val editor = sharedPrefs.edit()
        val editorInterface = EditorInterface(editor)
        val r = editorInterface.body()
        editor.apply()
        return r
    }
}