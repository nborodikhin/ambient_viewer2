package com.pinnacleimagingsystems.ambientviewer2

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.pinnacleimagingsystems.ambientviewer2.als.LightSensor
import com.pinnacleimagingsystems.ambientviewer2.camera.CameraViewModel
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@SuppressLint("StaticFieldLeak")
object Deps {
    val bgExecutor: Executor = Executors.newSingleThreadExecutor()

    lateinit var applicationContext: Context
    lateinit var mainExecutor: Executor
    lateinit var contentResolver: ContentResolver
    lateinit var cameraViewModel: CameraViewModel
    lateinit var lightSensor: LightSensor

    lateinit var prefs: SharedPreferences

    lateinit var algorithm: Algorithm

    private lateinit var mainHandler: Handler

    fun init(applicationContext: Context) {
        this.applicationContext = applicationContext
        mainHandler = Handler(Looper.getMainLooper())
        mainExecutor = Executor { runnable -> mainHandler.post(runnable) }
        contentResolver = applicationContext.contentResolver
        prefs = applicationContext.getSharedPreferences("Prefs", Context.MODE_PRIVATE)
        algorithm = AlgorithImpl()
    }
}