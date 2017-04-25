package io.slychat.messenger.android.activites

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.support.v7.widget.Toolbar
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import android.content.Intent
import android.net.Uri
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import io.slychat.messenger.android.activites.services.FileListAdapter
import android.provider.OpenableColumns
import io.slychat.messenger.android.activites.services.AndroidDirEntry
import io.slychat.messenger.android.activites.services.AndroidLocalFileData
import io.slychat.messenger.android.activites.services.DirEntryComparator
import io.slychat.messenger.android.activites.services.impl.AndroidStorageServiceImpl
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.DuplicateFilePathException
import io.slychat.messenger.services.files.RemoteFileEvent

class BrowseFileActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        val EXTRA_BROWSE_PATH = "io.slychat.messenger.android.activities.BrowseFileActivity.currentPath"
        val PICKFILE_REQUEST_CODE = 1
        val PICKFOLDER_REQUEST_CODE = 2
        val ROOT_DIRECTORY = "/"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp

    private lateinit var androidStorageService: AndroidStorageServiceImpl

    private lateinit var mOpenedDirView: TextView
    private lateinit var fileList: ListView
    private lateinit var fileListAdapter: FileListAdapter

    private var dialog: AlertDialog? = null

    private var currentFileUploading: Intent? = null
    private var currentPath = ROOT_DIRECTORY
    private val pathHistory = mutableListOf<String>()

    private var contextMenuFileId: String? = null
    private var contextMenuFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra(EXTRA_BROWSE_PATH))
            currentPath = intent.getStringExtra(EXTRA_BROWSE_PATH)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_browse_files)

        val actionBar = findViewById(R.id.browse_files_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.browse_file_title)
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val navigationView = findViewById(R.id.nav_browse_file_view) as NavigationView
        navigationView.setNavigationItemSelectedListener(this)

        app = AndroidApp.get(this)

        androidStorageService = AndroidStorageServiceImpl(this)

        val mDrawerName = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_name) as TextView
        val mDrawerEmail = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_email) as TextView

        mDrawerEmail.text = app.accountInfo?.email
        mDrawerName.text = app.accountInfo?.name

        mOpenedDirView = findViewById(R.id.opened_dir_name) as TextView
        fileList = findViewById(R.id.browse_files_list) as ListView
        fileListAdapter = FileListAdapter(baseContext, mutableListOf(), this)
        fileList.adapter = fileListAdapter

        loadDirectory(currentPath)
    }

    private fun init() {
        setListeners()
    }

    fun loadDirectory(path: String, fromHistory: Boolean = false) {
        fileListAdapter = FileListAdapter(baseContext, mutableListOf(), this)
        fileList.adapter = fileListAdapter
        androidStorageService.getEntriesAt(0, 30, path) successUi { files ->
            setCurrentPath(path, fromHistory)
            fileListAdapter.addAllFiles(files.sortedWith(DirEntryComparator()))
        } failUi {
            log.error("Something failed {}", it.message, it)
        }
    }

    private fun setCurrentPath(path: String, fromHistory: Boolean) {
        if (!fromHistory && path != ROOT_DIRECTORY)
            pathHistory.add(currentPath)

        currentPath = path
        mOpenedDirView.text = path
    }

    private fun openFileUploaderDialog() {
        val formLayout = LayoutInflater.from(this).inflate(R.layout.upload_file_dialog, null)
        val mBrowseBtn = formLayout.findViewById(R.id.upload_file_browse_btn) as Button
        mBrowseBtn.setOnClickListener {
            startPickFileIntent()
        }

        val builder = AlertDialog.Builder(this)
        builder.setView(formLayout)
        builder.setPositiveButton(resources.getString(R.string.upload_button), null)

        builder.setNegativeButton(resources.getString(R.string.cancel_button), { dialog, id ->
            dialog.dismiss()
        })

        dialog = builder.create()

        dialog?.setOnShowListener { d ->
            val alertDialog = d as AlertDialog
            val button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val fileData = currentFileUploading
                if (fileData != null) {
                    val mFileName = d.findViewById(R.id.upload_file_name_update) as EditText
                    val mFilePath = d.findViewById(R.id.upload_directory_update) as EditText

                    val fileName = mFileName.text.toString()
                    val filePath = mFilePath.text.toString()
                    if (fileName.isEmpty()) {
                        mFileName.error = resources.getString(R.string.upload_file_name_required_error)
                    }
                    else if (filePath.isEmpty()) {
                        mFilePath.error = resources.getString(R.string.upload_file_path_required_error)
                    }
                    else {

                        androidStorageService.uploadFile(fileData.data.toString(), filePath, fileName, false) successUi {
                            d.dismiss()
                        } failUi {
                            if (it is DuplicateFilePathException)
                                mFileName.error = resources.getString(R.string.upload_file_name_exist_error)
                            else
                                log.error("something failed {}", it.message, it)
                        }
                    }
                }
            }
        }

        dialog?.show()
    }

    private fun displayFileInformation(data: Intent) {
        val alertDialog = dialog
        alertDialog ?: return

        val mFileName = alertDialog.findViewById(R.id.upload_file_name_update) as EditText

        val fileInfo = getFileData(data.data)
        mFileName.setText(fileInfo?.name)
    }

    fun getFileData(uri: Uri): AndroidLocalFileData? {
        var localFileData: AndroidLocalFileData? = null
        val cursor = contentResolver.query(uri, null, null, null, null, null)

        cursor.use {
            if (cursor != null && cursor.moveToFirst()) {
                val displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val size: String?
                if (!cursor.isNull(sizeIndex)) {
                    size = cursor.getString(sizeIndex)
                } else {
                    size = null
                }

                localFileData = AndroidLocalFileData(displayName, size?.toLong())
            }
        }

        return localFileData
    }

    private fun startPickFileIntent() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICKFILE_REQUEST_CODE)
    }

    private fun handleRemoteFileEvent(event: RemoteFileEvent) {
        when (event) {
            is RemoteFileEvent.Added -> {
                addNewFilesToList(event.files)
            }

            is RemoteFileEvent.Deleted -> {
                fileListAdapter.deleteFiles(event.files)
            }

            is RemoteFileEvent.Updated -> {
                fileListAdapter.updateFiles(event.files)
            }
        }
    }

    private fun addNewFilesToList(files: List<RemoteFile>) {
        val dirEntryList = mutableListOf<AndroidDirEntry>()

        files.forEach { file ->
            val fileDir = file.userMetadata.directory
            if (fileDir == currentPath)
                dirEntryList.add(AndroidDirEntry.F(file))
            else {
                if (isInCurrentDirectory(fileDir)) {
                    val newDirName = getDirectoryName(fileDir)
                    newDirName ?: return
                    dirEntryList.add(0, AndroidDirEntry.D(fileDir, newDirName))
                }
            }
        }

        fileListAdapter.addAllFilesIfNotExist(dirEntryList)
    }

    private fun isInCurrentDirectory(directory: String): Boolean {
        return directory.startsWith(currentPath)
    }

    fun getDirectoryName(dir: String): String? {
        val splittedPath = dir.split(currentPath)

        return if (splittedPath.size == 2) {
            val splittedDir = splittedPath[1].split("/")
            return if (currentPath == "/" && splittedDir.size == 1 && splittedDir[0] != "/")
                splittedDir[0]
            else if (splittedDir.size > 1)
                splittedDir[1]
            else
                null
        }
        else
            null
    }

    private fun setListeners() {
        androidStorageService.addRemoteFileListener { event ->
            handleRemoteFileEvent(event)
        }
    }

    private fun clearListners() {
        androidStorageService.clearListeners()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val mFileId = v.findViewById(R.id.file_id) as TextView
        val mFileName = v.findViewById(R.id.file_name) as TextView
        contextMenuFileId = mFileId.text.toString()
        contextMenuFileName = mFileName.text.toString()

        val inflater = menuInflater
        inflater.inflate(R.menu.file_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val fileId = contextMenuFileId
        when (item.itemId) {
            R.id.delete_file -> {
                if (fileId != null) {
                    androidStorageService.deleteFiles(listOf(fileId)) failUi {
                        log.error("Something failed {}", it.message, it)
                    }
                }
                return true
            }
            R.id.download_file -> {
                if (fileId != null) {
                    androidStorageService.getFile(fileId) successUi { file ->
                        if (file != null) {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, file.userMetadata.fileName)
                                type = file.fileMetadata?.mimeType
                            }

                            startActivityForResult(intent, PICKFOLDER_REQUEST_CODE)
                        }
                    } failUi {
                        log.error("Something failed {}", it.message, it)
                    }
                }
                return true
            }
            R.id.share_file -> {
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }


    override fun onBackPressed() {
        val drawer = findViewById(R.id.browse_file_drawer_layout) as DrawerLayout
        val current = currentPath
        val history = pathHistory
        if (drawer.isDrawerOpen(GravityCompat.END))
            drawer.closeDrawer(GravityCompat.END)
        else if (current != "/" && history.size > 0 && history.last() != current) {
            loadDirectory(history.last(), true)
            history.removeAt(history.lastIndex)
        }
        else
            super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.layout_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_menu -> {
                val mDrawerLayout = findViewById(R.id.browse_file_drawer_layout) as DrawerLayout
                mDrawerLayout.openDrawer(Gravity.END)
            }
            android.R.id.home -> { onBackPressed() }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val drawer = findViewById(R.id.browse_file_drawer_layout) as DrawerLayout
        when(item.itemId) {
            R.id.upload_file -> {
                drawer.closeDrawer(GravityCompat.END)
                openFileUploaderDialog()
            }
            R.id.manage_transfer -> {
                drawer.closeDrawer(GravityCompat.END)
                startActivity(Intent(baseContext, ManageTransferActivity::class.java))
            }
        }

        drawer.closeDrawer(GravityCompat.END)
        return true
    }


    override fun onPause() {
        super.onPause()
        clearListners()
    }

    override fun onResume() {
        super.onResume()
        init()
    }

    override fun onStop() {
        super.onStop()
        clearListners()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICKFILE_REQUEST_CODE) {
                data ?: return

                currentFileUploading = data

                displayFileInformation(data)
            }
            else if (requestCode == PICKFOLDER_REQUEST_CODE) {
                data ?: return

                val fileId = contextMenuFileId
                fileId ?: return

                androidStorageService.downloadFile(fileId, data.data.toString()) successUi {
                    // TODO Warn the user that the transfer started
                } failUi {
                    log.error("Something failed {}", it.message, it)
                }
            }
        }
    }
}