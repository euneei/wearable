/*
package com.example.ipin_watch_ble_collect_kt.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.ipin_watch_ble_collect_kt.domain.SensorData
import java.io.IOException
import java.security.KeyException

class SensorSession(
    private val context: Context,
    private val folderName: String,
    private val timeStamp: String,
    private val header: String
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensors: HashMap<String, Sensor?> = HashMap()
    private lateinit var fileStreamer: FileStreamer

    private var isFileStreamerCreated = false

    private val accDataList = ArrayList<SensorData>()
    private val gyroDataList = ArrayList<SensorData>()
    private val grvDataList = ArrayList<SensorData>()

    fun registerSensors(samplingRate: Int) {
        for (sensor in sensors.values) {
            sensorManager.registerListener(this, sensor, samplingRate)
        }
    }

    fun unregisterSensors() {
        for (sensor in sensors.values) {
            sensorManager.unregisterListener(this, sensor)
        }
    }

    fun startSession() {
        fileStreamer = FileStreamer(folderName, header)
        try {
            for (key in sensors.keys) {
                fileStreamer.addFile(key, "${key}_$timeStamp.txt")
            }
            isFileStreamerCreated = true
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }

        sensors["acc"] = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensors["gyro"] = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensors["game_rv"] = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        val samplingRate = 5000 // 200hz
        registerSensors(samplingRate)
    }

    fun stopSession() {
        unregisterSensors()

        if (isFileStreamerCreated) {
            try {
                fileStreamer.endFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onSensorChanged(sensorEvent: SensorEv
    ent) {
        val sensor = sensorEvent.sensor
        val timestamp = sensorEvent.timestamp
        if (timestamp == 0L) return

        try {
            when (sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accDataList.add(
                    SensorData(
                        timestamp,
                        sensorEvent.values
                    )
                )

                Sensor.TYPE_GYROSCOPE -> gyroDataList.add(
                    SensorData(
                        timestamp,
                        sensorEvent.values
                    )
                )

                Sensor.TYPE_GAME_ROTATION_VECTOR -> grvDataList.add(
                    SensorData(
                        timestamp,
                        sensorEvent.values
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not needed for your implementation
    }

    fun saveData() {
        fileStreamer = FileStreamer(folderName, header)
        try {
            for (key in sensors.keys) {
                fileStreamer.addFile(key, "${key}_$timeStamp.txt")
            }
            isFileStreamerCreated = true
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }

        val accStringBuilder = StringBuilder()
        for (data in accDataList) {
            accStringBuilder.append(data.getFileFormat())
        }
        accDataList.clear()
        try {
            fileStreamer.addRecord("acc", accStringBuilder.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: KeyException) {
            e.printStackTrace()
        }

        val gyroStringBuilder = StringBuilder()
        for (data in gyroDataList) {
            gyroStringBuilder.append(data.getFileFormat())
        }
        gyroDataList.clear()
        try {
            fileStreamer.addRecord("gyro", gyroStringBuilder.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: KeyException) {
            e.printStackTrace()
        }

        val grvStringBuilder = StringBuilder()
        for (data in grvDataList) {
            grvStringBuilder.append(data.getFileFormat())
        }
        grvDataList.clear()
        try {
            fileStreamer.addRecord("game_rv", grvStringBuilder.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: KeyException) {
            e.printStackTrace()
        }
    }
}

*/