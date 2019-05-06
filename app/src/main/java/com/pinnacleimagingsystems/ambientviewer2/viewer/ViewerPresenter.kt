package com.pinnacleimagingsystems.ambientviewer2.viewer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.pinnacleimagingsystems.ambientviewer2.*
import com.pinnacleimagingsystems.ambientviewer2.camera.ColorInfo
import com.pinnacleimagingsystems.ambientviewer2.camera.Gains
import com.pinnacleimagingsystems.ambientviewer2.camera.Matrix
import com.pinnacleimagingsystems.ambientviewer2.tasks.CopyTask
import java.io.File

abstract class ViewerPresenter: ViewModel() {
    enum class State {
        UNINITIALIZED,
        LOADING,
        DISPLAYING,
        PROCESSING,
    }

    fun State?.notLoaded() = when (this) {
        State.UNINITIALIZED,
        State.LOADING,
        null -> true
        else -> false
    }

    enum class ImageType {
        ORIGINAL,
        WORKING
    }

    data class Image(
            val type: ImageType,
            val bitmap: Bitmap,
            val parameters: AlgorithmParameters? = null
    )

    sealed class Event {
        object NonSrgbWarning: Event()
        data class UnsupportedFileType(val mimetype: String): Event()
        data class ReadError(val exception: Exception): Event()
        data class LightSensorParameterComputed(val parameter: Float): Event()

        fun asConsumable(): ConsumableEvent<Event> = ConsumableEvent(this)
    }

    class ViewerState {
        val displayName by lazy { MutableLiveData<String>() }
        val state by lazy { MutableLiveData<State>().apply { value = State.UNINITIALIZED } }

        val curParameter by lazy { MutableLiveData<Float>() }
        val curColorInfo by lazy { MutableLiveData<ColorInfo>() }
        val originalImage by lazy { MutableLiveData<Image>() }
        var filePath: String? = null
        var workingImage: Image? = null

        val displayingImage by lazy { MutableLiveData<Image>() }

        val event by lazy { MutableLiveData<ConsumableEvent<Event>>() }
    }

    val state = ViewerState()

    abstract fun startFlow(): Boolean
    abstract fun loadFile(file: String)
    abstract fun onSetParameter(parameter: Float, manualInput: Boolean)
    abstract fun onColorInfoChanged(colorInfo: ColorInfo)
    abstract fun onImageClicked()
    abstract fun onLightSensorChanged()
}

class ViewerPresenterImpl: ViewerPresenter() {
    private val contentResolver = Deps.contentResolver
    private val bgExecutor = Deps.bgExecutor
    private val mainExecutor = Deps.mainExecutor
    private val algorithm = Deps.algorithm
    private val colorInfo = Deps.cameraViewModel.colorInfo
    private val lightSensor by lazy { Deps.lightSensor }

    private var screenMaxSize: Int = 0

    private lateinit var workingBitmap: Bitmap

    private var currentProcessingId = 0

    private var enableContinuousUpdate = false

    fun init(windowsManager: WindowManager, enableContinuousUpdate: Boolean) {
        val displayMetrics = DisplayMetrics()
        windowsManager.defaultDisplay.getMetrics(displayMetrics)
        screenMaxSize = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
        this.enableContinuousUpdate = enableContinuousUpdate
    }

    override fun startFlow(): Boolean {
        if (state.curParameter.value == null) {
            val lux = lightSensor.value.value?.toInt() ?: 0
            state.curParameter.value = algorithm.meta.defaultParameter(lux)
            state.curColorInfo.value = colorInfo.value
            return true
        } else {
            return false
        }
    }

    override fun onLightSensorChanged() {
        if (!enableContinuousUpdate || state.state.value.notLoaded()) {
            return
        }

        val lux = lightSensor.value.value?.toInt() ?: 0
        val parameter = algorithm.meta.defaultParameter(lux)
        state.event.postValue(Event.LightSensorParameterComputed(parameter = parameter).asConsumable())
    }

    override fun loadFile(file: String) {
        state.filePath = file

        if (state.state.value!! != State.UNINITIALIZED) {
            return
        }

        var fileName = file

        state.state.value = State.LOADING

        val uri = Uri.parse(fileName)
        val displayName = uri.toDisplayName(contentResolver)
        state.displayName.value = displayName

        val copy = CopyTask(Deps.applicationContext)

        bgExecutor.execute {
            val temporary: Boolean
            val copyResult = if (uri.scheme == "file" || fileName.startsWith('/')) {
                temporary = false
                val contentResolver = Deps.applicationContext.contentResolver
                val mimeType = contentResolver.getType(uri)

                CopyTask.CopyResult.Success(mimeType ?: "image/jpeg", File(fileName))
            } else {
                temporary = true
                copy.copyFile(uri, displayName)
            }

            when (copyResult) {
                is CopyTask.CopyResult.UnsupportedType -> {
                    state.event.postValue(Event.UnsupportedFileType(copyResult.mimeType).asConsumable())
                    return@execute
                }
                is CopyTask.CopyResult.Failure -> {
                    state.event.postValue(Event.ReadError(copyResult.exception).asConsumable())
                    return@execute
                }
                is CopyTask.CopyResult.Success -> {
                    fileName = copyResult.file.absolutePath
                }
            }

            val bitmap: Bitmap
            val exif: androidx.exifinterface.media.ExifInterface

            try {
                bitmap = loadBitmap(fileName, screenMaxSize)
                exif = androidx.exifinterface.media.ExifInterface(fileName)
            } finally {
                if (temporary) {
                    File(fileName).delete()
                }
            }

            val colorSpaceInt = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_COLOR_SPACE, androidx.exifinterface.media.ExifInterface.COLOR_SPACE_UNCALIBRATED)
            if (colorSpaceInt != androidx.exifinterface.media.ExifInterface.COLOR_SPACE_S_RGB) {
                state.event.postValue(Event.NonSrgbWarning.asConsumable())
            }

            workingBitmap = bitmap.copy(bitmap.config, true)

            val originalImage = Image(ImageType.ORIGINAL, bitmap)
            state.originalImage.postValue(originalImage)

            mainExecutor.execute {
                processImage(setWorking = true)
            }

            state.displayingImage.postValue(originalImage)
        }
    }

    override fun onImageClicked() {
        switchDisplayingImages()
    }

    override fun onSetParameter(parameter: Float, manualInput: Boolean) {
        // note: direct comparing floats
        if (state.curParameter.value == parameter) {
            return
        }
        state.curParameter.postValue(parameter)

        state.state.value = State.PROCESSING

        processImage(parameterToUse = parameter, setWorking = manualInput)
    }

    override fun onColorInfoChanged(colorInfo: ColorInfo) {
        state.state.value = State.PROCESSING
        state.curColorInfo.postValue(colorInfo)
        processImage(colorInfoToUse = colorInfo, setWorking = false)
    }

    private fun processImage(parameterToUse: Float? = null, colorInfoToUse: ColorInfo? = null, setWorking: Boolean) {
        currentProcessingId++
        val processingId = currentProcessingId

        bgExecutor.execute {
            val parameter = parameterToUse ?: state.curParameter.value!!
            val colorInfo = colorInfoToUse ?: state.curColorInfo.value!!

            val originalBitmap = state.originalImage.value!!.bitmap

            val parameters = AlgorithmParameters(
                    parameter = parameter,
                    colorInfo = colorInfo,
                    useColorInfo = true
            )

            if (processingId != currentProcessingId) {
                // don't update state here: there is a next request, it will set the state in the end
                return@execute
            }

            algorithm.init(parameter, colorInfo.asAlgorithmColorInfo())
            updateBitmap(originalBitmap, workingBitmap)

            val image = Image(ImageType.WORKING, workingBitmap, parameters)
            state.workingImage = image
            onWorkingImageReady(setWorking)

            state.state.apply {
                postValue(State.DISPLAYING)
            }
        }
    }

    @AnyThread
    fun onWorkingImageReady(setWorking: Boolean) {
        val currentImage = state.displayingImage.value ?: return

        if (!setWorking && currentImage.type == ImageType.ORIGINAL) return

        setDisplayingImage(ViewerPresenter.ImageType.WORKING)
    }

    @AnyThread
    fun switchDisplayingImages() {
        val currentImage = state.displayingImage.value ?: return

        when (currentImage.type) {
            ViewerPresenter.ImageType.ORIGINAL -> setDisplayingImage(ViewerPresenter.ImageType.WORKING)
            ViewerPresenter.ImageType.WORKING -> setDisplayingImage(ViewerPresenter.ImageType.ORIGINAL)
        }
    }

    @AnyThread
    fun setDisplayingImage(type: ViewerPresenter.ImageType) {
        when (type) {
            ViewerPresenter.ImageType.ORIGINAL -> state.originalImage.value?.let { image ->
                state.displayingImage.postValue(image)
            }
            ViewerPresenter.ImageType.WORKING -> state.workingImage?.let{ image ->
                state.displayingImage.postValue(image)
            }
        }
    }

    @WorkerThread
    private fun updateBitmap(origBitmap: Bitmap, newBitmap: Bitmap) {
        val width = origBitmap.width
        val height = origBitmap.height

        val pixels = IntArray(width * height)

        origBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        algorithm.apply(pixels, width, height)

        newBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun ColorInfo.asAlgorithmColorInfo()
        = Algorithm.ColorInfo(rgbGains.asAlgorithmGains(), matrix.asAlgorithmMatrix())

private fun Gains.asAlgorithmGains()
        = Algorithm.Gains(r, g, b)

private fun Matrix.asAlgorithmMatrix() = Algorithm.Matrix(
        elements[0].toFloat(), elements[1].toFloat(), elements[2].toFloat(),
        elements[3].toFloat(), elements[4].toFloat(), elements[5].toFloat(),
        elements[6].toFloat(), elements[7].toFloat(), elements[8].toFloat()
)
