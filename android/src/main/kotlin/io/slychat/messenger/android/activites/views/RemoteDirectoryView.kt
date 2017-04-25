package io.slychat.messenger.android.activites.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.BrowseFileActivity
import io.slychat.messenger.android.activites.services.AndroidDirEntry

class RemoteDirectoryView(
        directoryInfo: AndroidDirEntry.D?,
        context: Context,
        attrs: AttributeSet?) : LinearLayout(context, attrs) {

    constructor(context: Context, attrs: AttributeSet?): this(null, context, attrs)

    private val activity = context as BrowseFileActivity

    init {
        LayoutInflater.from(context).inflate(R.layout.remote_directory, this, true)

        if (directoryInfo != null) {
            val mDirName = findViewById(R.id.directory_name) as TextView
            mDirName.text = directoryInfo.name

            setClickListener(directoryInfo)
        }
    }

    private fun setClickListener(dirInfo: AndroidDirEntry.D) {
        this.setOnClickListener {
            activity.loadDirectory(dirInfo.fullPath)
        }
    }
}