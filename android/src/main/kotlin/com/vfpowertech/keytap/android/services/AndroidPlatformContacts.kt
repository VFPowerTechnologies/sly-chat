package com.vfpowertech.keytap.android.services

import android.Manifest
import android.content.AsyncQueryHandler
import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import com.vfpowertech.keytap.android.AndroidApp
import com.vfpowertech.keytap.core.PlatformContact
import com.vfpowertech.keytap.core.PlatformContactBuilder
import com.vfpowertech.keytap.services.PlatformContacts
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.successUi
import java.util.*

class AndroidPlatformContacts(private val context: Context) : PlatformContacts {
    override fun fetchContacts(): Promise<List<PlatformContact>, Exception> {
        val deferred = deferred<List<PlatformContact>, Exception>()

        //AsyncQueryHandlers must be created on threads which have called Looper.prepare
        //so can't just create it on an arbitrary thread
        val handler = Handler(context.mainLooper)
        handler.post {
            val androidApp = AndroidApp.get(context)
            androidApp.requestPermission(Manifest.permission.READ_CONTACTS) successUi { granted ->
                if (granted)
                    createAsyncQueryHandler(deferred)
                else
                    deferred.resolve(ArrayList())
            }
        }

        return deferred.promise
    }

    private fun createAsyncQueryHandler(deferred: Deferred<List<PlatformContact>, Exception>) {
        val asyncHandler = object : AsyncQueryHandler(context.contentResolver) {
            override fun onQueryComplete(token: Int, cookie: Any?, cursor: Cursor?) {
                if (cursor == null) {
                    deferred.reject(RuntimeException("Null cursor"))
                    return
                }

                try {
                    deferred.resolve(realOnQueryComplete(cursor))
                }
                catch (e: Exception) {
                    deferred.reject(e)
                }
            }

            private fun realOnQueryComplete(cursor: Cursor): List<PlatformContact> {
                val builders = HashMap<Int, PlatformContactBuilder>()

                while (cursor.moveToNext()) {
                    val email = cursor.getString(0)
                    val phoneNumber = cursor.getString(1)
                    val name = cursor.getString(2)
                    val id = cursor.getInt(3)
                    val mimetype = cursor.getString(4)

                    var builder = builders[id]
                    if (builder == null) {
                        builder = PlatformContactBuilder()
                        builders[id] = builder
                    }

                    when (mimetype) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> builder.name = name
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> builder.emails.add(email)
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> builder.phoneNumbers.add(phoneNumber)
                    }
                }

                //certain contacts may not have display names; these don't show up in the android contacts list, so just ignore them
                return builders.filterValues { it.name != null }.map { it.value.build() }
            }
        }

        //the data cols here are all the same cols in reality, but we shouldn't rely on that
        val projection = arrayOf(CommonDataKinds.Email.ADDRESS, CommonDataKinds.Phone.NUMBER, CommonDataKinds.StructuredName.DISPLAY_NAME, Data.CONTACT_ID, Data.MIMETYPE)
        val selection = "${Data.MIMETYPE} IN ('${CommonDataKinds.Email.CONTENT_ITEM_TYPE}', '${CommonDataKinds.Phone.CONTENT_ITEM_TYPE}', '${CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}')"
        asyncHandler.startQuery(
            1,
            null,
            Data.CONTENT_URI,
            projection,
            selection,
            null,
            null
        )
    }
}