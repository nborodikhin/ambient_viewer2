package com.pinnacleimagingsystems.ambientviewer2.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import android.media.ImageReader
import android.util.Rational
import android.view.Surface

const val GAINS_SCAN_INERVAL = 1000L

interface CameraViewModel {
    enum class State {
        UNINITIALIZED,
        NEED_PERMISSION,
        INITIALIZED
    }

    val state: LiveData<State>
    val cameraManager: CameraManager?

    val currentCamera: LiveData<String>
    val colorInfo: LiveData<ColorInfo>

    fun initialize()

    fun getCameraList(): List<String>

    fun getCameraCharacteristics(cameraId: String): CameraCharacteristics?

    fun cameraSelected(cameraId: String?)
}

class CameraViewModelImpl(
        applicationContext: Context
): CameraViewModel, LifecycleObserver {
    val applicationContext = applicationContext
    override var cameraManager: CameraManager? = null

    var isResumed = false

    val handlerThread = HandlerThread("cameraHandler")
    val bgHandler by lazy { Handler(handlerThread.looper) }

    override var state = MutableLiveData<CameraViewModel.State>().apply {
        value = CameraViewModel.State.UNINITIALIZED
    }

    override val colorInfo = MutableLiveData<ColorInfo>().apply {
        val rationalOne = Rational(1, 1)
        val rationalZero = Rational.ZERO
        val identityMatrix = Matrix(arrayOf(
                rationalOne, rationalZero, rationalZero,
                rationalZero, rationalOne, rationalZero,
                rationalZero, rationalZero, rationalOne
        ))
        val identityGains = Gains(1.0f, 1.0f, 1.0f)

        value = ColorInfo(identityGains, identityMatrix)
    }

    override val currentCamera = MutableLiveData<String>()

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        initialize()
        handlerThread.start()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        isResumed = true
        startCameraListener()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        isResumed = false
        stopCameraListener()
    }

    @MainThread
    override fun initialize() {
        if (state.value == CameraViewModel.State.INITIALIZED) {
            return
        }

        val result = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA)
        if (result == PackageManager.PERMISSION_GRANTED) {
            cameraManager = applicationContext.getSystemService(CameraManager::class.java)
            state.postValue(CameraViewModel.State.INITIALIZED)
        } else {
            state.postValue(CameraViewModel.State.NEED_PERMISSION)
        }
    }

    override fun getCameraList()
            = cameraManager?.cameraIdList?.asList() ?: listOf()

    override fun getCameraCharacteristics(cameraId: String)
            = cameraManager?.getCameraCharacteristics(cameraId)


    override fun cameraSelected(cameraId: String?) {
        if (isResumed) {
            stopCameraListener()
            currentCamera.value = cameraId
            startCameraListener()
        } else {
            currentCamera.value = cameraId
        }
    }

    var openedCamera: CameraDevice? = null

    @SuppressLint("MissingPermission")
    fun startCameraListener() {
        val currentCamera = currentCamera.value ?: return

        val callback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                openedCamera = camera
                onCameraOpened(openedCamera!!)
            }

            override fun onClosed(camera: CameraDevice) {
                if (openedCamera != camera) {
                    return
                }

                openedCamera = null
            }

            override fun onDisconnected(camera: CameraDevice) {
                if (openedCamera != camera) {
                    return
                }

                Toast.makeText(applicationContext, "Camera disconnected", Toast.LENGTH_SHORT)
                openedCamera = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                if (openedCamera != camera) {
                    return
                }

                Toast.makeText(applicationContext, "Camera error: $error", Toast.LENGTH_SHORT)
            }
        }

        cameraManager?.openCamera(
                currentCamera,
                callback,
                null)
    }

    var captureSession: CameraCaptureSession? = null
    var captureSurface: Surface? = null
    var imageReader: ImageReader? = null

    fun stopCameraListener() {
        openedCamera ?: return
        captureSession?.close()
        bgHandler.removeCallbacks(captureSingleFrame)
        openedCamera!!.close()
    }

    fun onCameraOpened(camera: CameraDevice) {
        val characteristics = cameraManager!!.getCameraCharacteristics(camera.id)
        val configMap = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!

        val sizes = configMap.getOutputSizes(ImageFormat.YUV_420_888).toMutableList()
        // select smallest size
        sizes.sortBy { size -> size.width }
        val size = sizes.first()

        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 1)
        imageReader!!.setOnImageAvailableListener(
                { reader ->
                    val image = reader.acquireLatestImage()
                    image.close()
                },
                bgHandler
        )
        captureSurface = imageReader!!.surface

        camera.createCaptureSession(
                listOf(captureSurface),
                object: CameraCaptureSession.StateCallback() {
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(applicationContext, "Failed to create capure session", Toast.LENGTH_SHORT)
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        onSessionConfigured(camera, session)
                    }
                },
                bgHandler)
    }

    lateinit var request: CaptureRequest

    fun onSessionConfigured(camera: CameraDevice, session: CameraCaptureSession) {
        request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply {
                    addTarget(captureSurface!!)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }.build()
        postSingleFrameCapture()
    }

    fun postSingleFrameCapture() {
        captureSession ?: return

        bgHandler.postDelayed(captureSingleFrame, GAINS_SCAN_INERVAL)
    }

    private val captureSingleFrame = Runnable {
        val session = captureSession ?: return@Runnable

        if (session.device != openedCamera) {
            // session is already closed
            return@Runnable
        }

        session.capture(
                request,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        val gains = result[CaptureResult.COLOR_CORRECTION_GAINS]!!
                        val matrix = result[CaptureResult.COLOR_CORRECTION_TRANSFORM]!!
                        val matrixValues = Array(9) { Rational.ZERO }
                        matrix.copyElements(matrixValues, 0)

                        colorInfo.postValue(
                                ColorInfo(
                                        Gains(gains.red, gains.greenEven, gains.blue),
                                        Matrix(matrixValues)
                                )
                        )

                        postSingleFrameCapture()
                    }
                },
                bgHandler
        )
    }
}