package com.pinnacleimagingsystems.ambientviewer2.als

import androidx.lifecycle.*
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class LightSensor: ViewModel(), LifecycleObserver {
    val presense = MutableLiveData<SensorPresence>().apply { value = SensorPresence.UNKNOWN }
    val value = MutableLiveData<Float>().apply { value = -1f }
    val accuracy = MutableLiveData<SensorAccuracy>().apply { value = SensorAccuracy.UNKNOWN }
    val resolution = MutableLiveData<Float>()

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null

    fun initSensor(context: Context) {
        if (presense.value != SensorPresence.UNKNOWN) return

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        presense.value = if (sensor != null) SensorPresence.PRESENT else SensorPresence.ABSENT
        resolution.value = sensor?.resolution ?: 0.0f
    }

    private val lightEventListener = object: SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            this@LightSensor.accuracy.postValue(when(accuracy) {
                SensorManager.SENSOR_STATUS_NO_CONTACT -> SensorAccuracy.NO_CONTACT
                SensorManager.SENSOR_STATUS_UNRELIABLE -> SensorAccuracy.UNRELIABLE
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracy.LOW
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracy.MEDIUM
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracy.HIGH
                else -> SensorAccuracy.UNKNOWN
            })
        }

        override fun onSensorChanged(event: SensorEvent) {
            value.postValue(event.values[0])
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun startSensor() {
        if (sensor != null) {
            sensorManager.registerListener(lightEventListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stopSensor() {
        sensorManager.unregisterListener(lightEventListener)
    }
}


