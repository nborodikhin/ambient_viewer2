package com.pinnacleimagingsystems.ambientviewer2.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.ProcessLifecycleOwner
import com.pinnacleimagingsystems.ambientviewer2.Deps
import com.pinnacleimagingsystems.ambientviewer2.als.LightSensor
import com.pinnacleimagingsystems.ambientviewer2.camera.CameraViewModelImpl

class ViewApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        val lightSensor = LightSensor()
        lightSensor.initSensor(this)

        val cameraViewModel = CameraViewModelImpl(this)

        with (ProcessLifecycleOwner.get().lifecycle) {
            addObserver(lifecycleObserver)
            addObserver(lightSensor)
            addObserver(cameraViewModel)
        }

        Deps.init(this)
        Deps.lightSensor = lightSensor
        Deps.cameraViewModel = cameraViewModel

        cameraViewModel.colorInfo.observe(
                ProcessLifecycleOwner.get(),
                Observer { gains -> Log.d("!!!", "Gains: $gains") }
        )
    }

    private val lifecycleObserver = object: LifecycleObserver {
        // TODO: subcsribe to sensor and camera
    }
}