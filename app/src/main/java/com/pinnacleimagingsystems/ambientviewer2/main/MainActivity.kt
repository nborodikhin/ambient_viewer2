package com.pinnacleimagingsystems.ambientviewer2.main

import android.Manifest
import android.app.Activity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import com.pinnacleimagingsystems.ambientviewer2.*
import com.pinnacleimagingsystems.ambientviewer2.main.MainPresenter.Action.*
import com.pinnacleimagingsystems.ambientviewer2.viewer.ViewerActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val PICK_IMAGE_CODE = 123
        const val REQUEST_PERMISSION = 125
        const val SELECT_MULIPLE_IMAGE_CODE = 126
        const val REQUEST_CAMERA_PERMISSION = 127
    }

    private val views by lazy {
        object {
            val loadImageButton: View = findViewById(R.id.load_image)
            val loadMultipleButton: View = findViewById(R.id.load_multiple_images)
            val event: TextView = findViewById(R.id.event)
            val loadLastButton: View = findViewById(R.id.load_last)
            val lastContainer: View = findViewById(R.id.last_file_container)
            val lastFileName: TextView = findViewById(R.id.last_file_name)
            val lastFilePreview: ImageView = findViewById(R.id.last_file_preview)
            val version: TextView = findViewById(R.id.version)
            val viewerMode: CheckBox = findViewById(R.id.viewer_mode)
            val cameraSelector: Spinner = findViewById(R.id.camera_selector)
        }
    }

    data class CameraEntry(val id: String, val desc: String)

    lateinit var presenter: MainPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_main)

        val lifecycleOwner = this

        presenter = ViewModelProviders.of(this)[MainPresenterImpl::class.java].apply {
            init(application)
        }

        views.apply {
            loadImageButton.setOnClickListener { presenter.action(LoadButtonClicked) }
            loadMultipleButton.setOnClickListener { presenter.action(LoadMultipleButtonClicked) }
            loadLastButton.setOnClickListener { onLoadLastClicked() }
            lastContainer.setOnClickListener { onLoadLastClicked() }
            cameraSelector.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    onCameraSelected(null)
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    @Suppress("UNCHECKED_CAST")
                    val descs = parent!!.tag as List<CameraEntry>
                    onCameraSelected(descs[position].id)
                }
            }
            version.text = getString(R.string.version, BuildConfig.VERSION_NAME)
        }

        with (presenter.state) {
            eventDescription.observe(lifecycleOwner, Observer { text ->
                views.event.text = text
            })

            event.observe(lifecycleOwner, Observer { event -> event!!.consume(::onEvent) })
            lastFile.observe(lifecycleOwner, Observer { lastFile -> onLastFileChanged(lastFile!!) })
        }
    }

    override fun onStart() {
        super.onStart()
        presenter.action(MainPresenter.Action.ActivityStarted)
    }

    private fun onEvent(event: MainPresenter.Event) {
        when(event) {
            is MainPresenter.Event.ViewFile -> {
                val files = arrayOf(event.file)
                val intent = Intent(this, ViewerActivity::class.java).apply {
                    putExtra(ViewerActivity.PARAM_FILES, files)
                    putExtra(ViewerActivity.PARAM_VIEWER_MODE, views.viewerMode.isChecked)
                }
                startActivity(intent)
            }
            is MainPresenter.Event.ViewFiles -> {
                val files = event.uris.map { it.toString() }.toTypedArray()
                val intent = Intent(this, ViewerActivity::class.java).apply {
                    putExtra(ViewerActivity.PARAM_FILES, files)
                    putExtra(ViewerActivity.PARAM_VIEWER_MODE, views.viewerMode.isChecked)
                }
                startActivity(intent)
            }
            MainPresenter.Event.RequestCameraPermission -> requestCameraPermission()
            MainPresenter.Event.CameraListReady -> readCameraList()
            MainPresenter.Event.LoadFile -> loadSingleImage()
            MainPresenter.Event.LoadMultipleFiles -> loadMultipleImages()
        }.exhaustive
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
        )
    }

    private fun readCameraList() {
        val cameraViewModel = Deps.cameraViewModel
        val cameraIds = cameraViewModel.getCameraList()
        val descs = mutableListOf<CameraEntry>()
        cameraIds.forEach { cameraId ->
            val chars = cameraViewModel.getCameraCharacteristics(cameraId)!!
            val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                else -> "unknown"
            }
            val desc = "$cameraId - $facing"
            descs.add(CameraEntry(cameraId, desc))
        }

        views.cameraSelector.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                descs.map { entry -> entry.desc }
        )

        val currentCameraIndex = cameraIds.indexOf(cameraViewModel.currentCamera.value ?: "nonexistent")
        if (currentCameraIndex != -1) {
            views.cameraSelector.setSelection(currentCameraIndex)
        }

        views.cameraSelector.tag = descs
    }

    private fun onCameraSelected(cameraId: String?) {
        presenter.action(CameraSelected(cameraId))
    }

    private fun loadSingleImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        if (intent.resolveActivity(packageManager) == null) {
            return
        }

        startActivityForResult(intent, PICK_IMAGE_CODE)
    }

    private fun loadMultipleImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        if (intent.resolveActivity(packageManager) == null) {
            return
        }

        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_images)), SELECT_MULIPLE_IMAGE_CODE)
    }

    private fun onLoadLastClicked() {
        presenter.action(LastFileClicked)
    }

    private fun onLastFileChanged(lastFile: File) {
        views.loadLastButton.isEnabled = true
        views.lastContainer.visibility = View.VISIBLE
        views.lastFileName.text = Uri.fromFile(lastFile).toDisplayName(contentResolver)
        views.lastFilePreview.setPreviewFromFile(lastFile)
    }

    private fun ImageView.setPreviewFromFile(file: File) {
        val thumbnailBitmap = loadThumbnailBitmap(file.absolutePath)
        if (thumbnailBitmap != null) {
            setImageBitmap(thumbnailBitmap)
        } else {
            setImageURI(Uri.fromFile(file))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val intentData = data?.data

        when(requestCode) {
            PICK_IMAGE_CODE -> {
                if (resultCode == RESULT_OK && intentData != null) {
                    presenter.action(FileSelected(intentData))
                }
            }
            SELECT_MULIPLE_IMAGE_CODE -> {
                val uris = getSelectedImages(data)
                if (resultCode == Activity.RESULT_OK && uris.isNotEmpty()) {
                    presenter.action(MultipleFilesSelected(uris))
                }
            }
        }
    }

    private fun getSelectedImages(intent: Intent?): List<Uri> {
        val result = mutableListOf<Uri>()
        if (intent == null) {
            return result
        }

        val intentData = intent.data
        if (intentData != null) {
            // single image
            return listOf(intentData)
        }

        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i) ?: continue
                val uri = item.uri ?: continue

                result.add(uri)
            }
        }

        return result
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    presenter.action(MainPresenter.Action.CameraPermissionGranted)
                }
            }
        }
    }
}
