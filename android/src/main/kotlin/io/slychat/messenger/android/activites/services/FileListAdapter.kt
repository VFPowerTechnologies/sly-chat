package io.slychat.messenger.android.activites.services

import android.widget.ArrayAdapter
import android.view.ViewGroup
import android.content.Context
import android.view.View
import io.slychat.messenger.android.activites.BrowseFileActivity
import io.slychat.messenger.android.activites.views.RemoteDirectoryView
import io.slychat.messenger.android.activites.views.RemoteFileView
import io.slychat.messenger.core.files.RemoteFile
import java.util.*

class FileListAdapter(context: Context, private val values: List<AndroidDirEntry>, val browseFileActivity: BrowseFileActivity) : ArrayAdapter<AndroidDirEntry>(context, -1, values) {
    private val mapData = mutableMapOf<String, Int>()

    private val subDirectoryList = mutableListOf<String>()

    private val comparator = DirEntryComparator()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val dirEntry = values[position]

        val view: View
        when (dirEntry) {
            is AndroidDirEntry.F -> {
                view = RemoteFileView(dirEntry.file, context, null)
                browseFileActivity.registerForContextMenu(view)
            }
            is AndroidDirEntry.D -> {
                view = RemoteDirectoryView(dirEntry, browseFileActivity, null)
            }
        }

        return view
    }

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }

    fun deleteFiles(files: List<RemoteFile>) {
        files.forEach {
            val position = mapData[it.id]
            if (position != null) {
                remove(values[position])
                mapData.remove(it.id)
            }
        }

        updateMap()
    }

    fun updateFiles(files: List<RemoteFile>) {
        files.forEach { file ->
            val position = mapData[file.id]
            if (position != null) {
                val item = values[position]
                when (item) {
                    is AndroidDirEntry.F -> {
                        item.file = file
                    }
                }
            }
        }

        notifyDataSetChanged()
    }

    fun addAllFiles(files: List<AndroidDirEntry>) {
        super.addAll(files)
        updateMap()
    }

    fun addAllFilesIfNotExist(files: List<AndroidDirEntry>) {
        files.forEach {
            when (it) {
                is AndroidDirEntry.D -> {
                    if (!subDirectoryList.contains(it.name))
                        insert(it, 0)
                }
                is AndroidDirEntry.F -> {
                    add(it)
                }
            }
        }

        updateMap()
    }

    override fun add(`object`: AndroidDirEntry) {
        super.add(`object`)
        updateMap()
    }

    private fun updateMap() {
        values.forEach {
            when (it) {
                is AndroidDirEntry.D -> { subDirectoryList.add(it.name) }
                is AndroidDirEntry.F -> {}
            }
        }
        sort(comparator)

        for((index, element) in values.withIndex()) {
            when (element) {
                is AndroidDirEntry.F -> { mapData.put(element.file.id, index) }
                is AndroidDirEntry.D -> { mapData.put(element.name, index) }
            }
        }
    }
}

class DirEntryComparator: Comparator<AndroidDirEntry> {

    override fun compare(lhs: AndroidDirEntry, rhs: AndroidDirEntry): Int {
        return when (lhs) {
            is AndroidDirEntry.D -> {
                if (rhs is AndroidDirEntry.D)
                    lhs.name.capitalize().compareTo(rhs.name.capitalize())
                else
                    -1
            }

            is AndroidDirEntry.F -> {
                if (rhs is AndroidDirEntry.F)
                    lhs.file.userMetadata.fileName.capitalize().compareTo(rhs.file.userMetadata.fileName.capitalize())
                else
                    1
            }

            else -> 0
        }
    }

}