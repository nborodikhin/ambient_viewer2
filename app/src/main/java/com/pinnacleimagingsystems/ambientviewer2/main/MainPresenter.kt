package com.pinnacleimagingsystems.ambientviewer2.main

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.net.Uri
import com.pinnacleimagingsystems.ambientviewer2.*
import com.pinnacleimagingsystems.ambientviewer2.camera.CameraViewModel
import com.pinnacleimagingsystems.ambientviewer2.tasks.CopyTask
import java.io.File

abstract class MainPresenter: ViewModel() {
    sealed class Event {
        object RequestCameraPermission: Event()
        object CameraListReady: Event()

        object LoadFile: Event()
        object LoadMultipleFiles: Event()

        data class ViewFile(val file: String): Event()
        data class ViewFiles(val uris: List<Uri>): Event()

        fun asConsumable(): ConsumableEvent<Event> = ConsumableEvent(this)
    }

    sealed class Action {
        object ActivityStarted: Action()
        object CameraPermissionGranted: Action()
        data class CameraSelected(val cameraId: String?): Action()
        object LoadButtonClicked: Action()
        object LoadMultipleButtonClicked: Action()
        data class FileSelected(val uri: Uri): Action()
        data class MultipleFilesSelected(val uris: List<Uri>): Action()
        object LastFileClicked: Action()
    }

    class State {
        val currentFile by lazy { MutableLiveData<File>() }
        val eventDescription by lazy { MutableLiveData<String>() }
        val lastFile by lazy { MutableLiveData<File>() }

        val event by lazy { MutableLiveData<ConsumableEvent<Event>>() }
    }

    val state = State()

    abstract fun action(action: Action)
}

class MainPresenterImpl: MainPresenter() {
    companion object {
        private const val TAG = "MainPresenterImpl"
    }

    private lateinit var context: Application
    private val contentResolver get() = context.contentResolver

    private val bgExecutor = Deps.bgExecutor
    private val mainExecutor = Deps.mainExecutor
    private val cameraViewModel = Deps.cameraViewModel

    private fun sendEvent(event: Event) {
        state.event.postValue(event.asConsumable())
    }

    override fun action(action: Action) {
        when(action) {
            is Action.FileSelected -> onFileSelected(action.uri)
            is Action.MultipleFilesSelected -> onMultipleFilesSelected(uris = action.uris)
            Action.LastFileClicked -> onLastFileClicked()
            Action.LoadButtonClicked -> onLoadSingleClicked()
            Action.LoadMultipleButtonClicked -> onLoadMultipleClicked()
            Action.ActivityStarted -> onActivityStarted()
            Action.CameraPermissionGranted -> onCameraPermissionGranted()
            is Action.CameraSelected -> onCameraSelected(action.cameraId)
        }.exhaustive
    }

    private fun onActivityStarted() {
        when (cameraViewModel.state.value) {
            CameraViewModel.State.UNINITIALIZED -> sendEvent(Event.RequestCameraPermission)
            CameraViewModel.State.NEED_PERMISSION -> sendEvent(Event.RequestCameraPermission)
            CameraViewModel.State.INITIALIZED -> onCameraPermissionGranted()
            null -> TODO()
        }.exhaustive
    }

    private fun onCameraPermissionGranted() {
        cameraViewModel.initialize()
        sendEvent(Event.CameraListReady)
    }

    private fun onCameraSelected(cameraId: String?) {
        cameraViewModel.cameraSelected(cameraId)
    }

    private fun onLoadSingleClicked() {
        val needPermission = cameraViewModel.state.value == CameraViewModel.State.NEED_PERMISSION
        sendEvent(when {
            needPermission -> Event.RequestCameraPermission
            else -> Event.LoadFile
        })
    }

    private fun onLoadMultipleClicked() {
        val needPermission = cameraViewModel.state.value == CameraViewModel.State.NEED_PERMISSION
        sendEvent( when {
            needPermission -> Event.RequestCameraPermission
            else -> Event.LoadMultipleFiles
        })
    }

    fun init(context: Application) {
        this.context = context
        val lastFile = Deps.prefs.getString(Prefs.LAST_NAME, null)
        if (lastFile != null) {
            val file = File(lastFile)
            if (file.exists()) {
                state.lastFile.value = file
            }
        }
    }

    fun onFileSelected(uri: Uri) {
        val displayName = uri.toDisplayName(contentResolver)

        val copy = CopyTask(context)

        fun deliverLoadResult(state: MainPresenter.State, uri: Uri, copyResult: CopyTask.CopyResult) {
            when(copyResult) {
                is CopyTask.CopyResult.UnsupportedType -> {
                    state.eventDescription.value = "Failed: unsupported type ${copyResult.mimeType}"
                }
                is CopyTask.CopyResult.Failure -> {
                    state.eventDescription.value = "Failed: exception ${copyResult.exception}"
                }
                is CopyTask.CopyResult.Success -> {
                    val file = copyResult.file

                    state.eventDescription.value = "loaded file $file of ${copyResult.mimeType} from $uri"
                    state.currentFile.value = file
                    state.event.value = Event.ViewFile(file.absolutePath).asConsumable()

                    Deps.prefs.edit().apply{
                        putString(Prefs.LAST_NAME, file.absolutePath)
                    }.apply()
                    state.lastFile.value = file
                }
            }
        }

        bgExecutor.execute {
            val copyResult = copy.copyFile(uri, displayName)

            mainExecutor.execute {
                deliverLoadResult(state, uri, copyResult)
            }
        }
    }

    fun onMultipleFilesSelected(uris: List<Uri>) {
        val count = uris.size
        state.eventDescription.value = "loaded $count files"
        state.event.value = Event.ViewFiles(uris).asConsumable()
    }

    fun onLastFileClicked() {
        val file = state.lastFile.value ?: return

        state.event.value = Event.ViewFile(file.absolutePath).asConsumable()
        state.eventDescription.value = "Loaded last: $file"
    }
}
