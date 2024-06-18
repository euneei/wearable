
package com.example.type_accelerometer


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import java.io.File
import java.io.FileWriter
import java.io.IOException
import androidx.databinding.DataBindingUtil.setContentView
import com.example.type_accelerometer.R
import com.example.type_accelerometer.databinding.LayoutBinding
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.sqrt




class MainActivity: ComponentActivity() {

    private var accelerometerSensor: Sensor? = null
    private val sensorData = StringBuilder()
    private val collectionDuration = 3000L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var binding: LayoutBinding
    private val accelerometerDataList = mutableListOf<Float>()
    private lateinit var mTextView: TextView
    private lateinit var mButMeasure: Button
    private lateinit var resultOutput: TextView
    private val APP_TAG: String = "IMU Logger"
    private val context: Context? = null
    private lateinit var sensorEventListener: SensorEventListener
    private var classi: ClassifierwithModel? = null
    private val isMeasurementRunning = AtomicBoolean(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setContentView(this, R.layout.layout)

        setContentView(binding.root)
        mTextView = binding.txtOutput
        mButMeasure = binding.butStart
        resultOutput = binding.resultOutput
//        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)


        classi =
            ClassifierwithModel(context = this, resultOutput = resultOutput, activity = this,
                mTextView = mTextView, mButMeasure = mButMeasure)
        Log.d(APP_TAG, "classi called")
        /*
        resultOutput.setOnClickListener { classi!!.startClassifier() }
        Log.d(APP_TAG, "classi.startClassifier called")

         */

        mButMeasure.setOnClickListener{ classi!!.startMeasurement() }
        Log.d(APP_TAG, "classi.데이터수집 called")




    }



    override fun onDestroy() {
        super.onDestroy()
        isMeasurementRunning.set(false)

        classi?.stopApp()
        Log.d(APP_TAG, "classi.stopApp called")

//        sensorManager.unregisterListener(this)
    }
}


