package com.example.type_accelerometer

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs

import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.hardware.SensorEventListener
import android.os.Build
import android.os.Environment
import android.view.WindowManager
import android.widget.Button
import com.example.type_accelerometer.ml.TestSixcnn
import com.example.type_accelerometer.ml.TestFivecnn
import com.example.type_accelerometer.ml.TestFourcnn
import com.example.type_accelerometer.ml.TestThreecnn
import com.example.type_accelerometer.ml.TestTwocnn
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.security.KeyException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min


open class ClassifierwithModel(private val context: Context,
                               private var resultOutput: TextView,
                               private var mTextView: TextView,
                               private var mButMeasure: Button,
                               private val activity: Activity,

                               ): SensorEventListener {
    private val APP_TAG: String = "IMU Logger"
    private val APP_TAG_TWO: String = "Result number"
    var fileStreamer: FileStreamer? = null
    private var sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensors: HashMap<String, Sensor?> = HashMap()
    //private lateinit var fileStreamer: FileStreamer
    private var accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    //private val sensorDataBuffer = mutableListOf<FloatArray>()
    private val dataInterval = 6500L // 6.5초
    private val IntervalTime = 30000L
    private var interpreter: Interpreter? = null
    private val accHandler = Handler(Looper.getMainLooper())
    // private val accTracker = AccelerometerTracker? = null
    private var accelerometerDataList = mutableListOf<Float>()
    private val isMeasurementRunning = AtomicBoolean(false)
    private val sensorDataBuffer = mutableListOf<String>()
    private var isInferring = false
    private var lastTime = AtomicLong(0) // 마지막 변화량이 감지된 시간을 저장



    private val accListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            Log.i(APP_TAG, "came into list")
            if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {

                val timestamp = event.timestamp
                val x = event.values[0]
//                Log.i(APP_TAG, "X: $x")
                val y = event.values[1]
//                Log.i(APP_TAG, "Y: $y")
                val z = event.values[2]
//                Log.i(APP_TAG, "Z: $z")
                val svm = sqrt(x.pow(2) + y.pow(2) + z.pow(2))
                accelerometerDataList.add(svm)

                // Log.i("count", accelerometerDataList.size.toString())
                Log.d(APP_TAG, "Accelerometer data: x=$x, y=$y, z=$z, svm = $svm")

                val dataString = "$timestamp\t$x\t$y\t$z"
                sensorDataBuffer.add(dataString)



                processData()

            } else {
                Log.i(APP_TAG, "onDataReceived List is zero")
            }
            // processData()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.d("Sensor", "Accuracy changed: $accuracy")

        }


        fun calculateRecentChangeSum(dataList: List<Float>): Double {
            var totalChange = 0.0

            if (accelerometerDataList.size >= 50) {
//                val recentData = accelerometerDataList.takeLast(50)

                // 변화량 계산
                for (i in 1 until 50) {
                    totalChange += abs(dataList[i] - dataList[i - 1])
                }
            }
            return totalChange
        }

        var lastInferenceTime = System.currentTimeMillis()
        var inferenceCount = 0
        var inferenceActive = false

        fun processData() {


            Log.d(APP_TAG, "processData 함수 호출됨")


            if(accelerometerDataList.size >= 150) {
                if(!inferenceActive) {
                    val changesum = calculateRecentChangeSum(accelerometerDataList.takeLast(150))
                    if(changesum >=3) {
                        inferenceActive = true
                    } else {
                        resultOutput.text = context.getString(R.string.nothing)
                        accelerometerDataList.clear()
                    }
                }

                if (inferenceActive) {
                    if(accelerometerDataList.size >= 300) {
                        val inferenceData = accelerometerDataList.takeLast(300)
                        inferFromData(inferenceData)
                        accelerometerDataList = accelerometerDataList.drop(150).toMutableList()
                    }
                }
            }

        }

        fun inferFromData(dataList: List<Float>) {
            try {
                val result = classifyexample(context, dataList)
                showResults(result)
                Log.d(APP_TAG, "showResults 호출")
            } catch (e: IOException) {
                Log.e(APP_TAG, "Error in processData: ${e.message}", e)
            } catch (e: KeyException) {
                Log.e(APP_TAG, "Error in processData: ${e.message}", e)
            }

            inferenceCount +=1
            if(inferenceCount >= 12) {
                resultOutput.text = context.getString(R.string.Resulting)
                accelerometerDataList.clear()
                inferenceActive = false
                inferenceCount = 0

            }
        }

    }


    fun startMeasurement() {
        if (!isMeasurementRunning.get()) {
            mTextView.text = context.getString(R.string.outputMeasuring)
            mButMeasure.text = context.getString(R.string.stop)
            isMeasurementRunning.set(true)
            (activity as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val streamFolder =
                "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}${File.separator}IMU${File.separator}${System.currentTimeMillis()}"
            val file = File(streamFolder)
            if(!file.exists()) {
                if(!file.mkdirs()){
                    Log.i(APP_TAG, "$streamFolder is not available to mkdirs")
                }
            }
            val header = "#\tdeviceModel ${Build.MODEL}"
            fileStreamer = FileStreamer(streamFolder, header)
            try {
                fileStreamer?.addFile("acc", "acc.txt")
            } catch (e: IOException) {
                e.message?.let { Log.e(APP_TAG, it) }
            }

            sensorManager.registerListener(accListener, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorDataBuffer.clear()    // 13:29
        } else {
            stopMeasurement()
        }

    }


    fun classifyexample(context: Context, accDataList: List<Float>): FloatArray {
        val model = TestSixcnn.newInstance(context)
        val byteBuffer = ByteBuffer.allocateDirect(accDataList.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        accDataList.forEach {
            byteBuffer.putFloat(it)
        }

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1,300,1), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)

        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        val resultData = outputFeature0.floatArray
        resultData.forEachIndexed{index, value -> println("Output $index: $value") }

        model.close()
        Log.d(APP_TAG_TWO, "outputbuffer: " + resultData[0])
        Log.d(APP_TAG_TWO, "outputbuffer2: " + resultData[1])
        Log.d(APP_TAG_TWO, "outputbuffer3: " + resultData[2])
        Log.d(APP_TAG_TWO, "outputbuffer4: " + resultData[3])



        return floatArrayOf(
            resultData[0],
            resultData[1],
            resultData[2],
            resultData[3]
        )

    }




    private fun showResults(inferenceResult: FloatArray) {
        val classLabels = arrayOf("Class A", "Class B", "Class C","Class D")

        val maxIndex = findMaxIndex(inferenceResult)
        val predictedClassLabel = classLabels[maxIndex]

        if (predictedClassLabel == "Class A") {
            (context as? Activity)?.runOnUiThread {
                resultOutput.text = context.getString(R.string.result0)

//                Handler(Looper.getMainLooper()).postDelayed({
//                    resultOutput.text = context.getString(R.string.Resulting)
//
//                }, 3000)
            }
        }
        if (predictedClassLabel == "Class B") {
            (context as? Activity)?.runOnUiThread {
                resultOutput.text = context.getString(R.string.result1)

//                Handler(Looper.getMainLooper()).postDelayed({
//                    resultOutput.text = context.getString(R.string.Resulting)
//
//                }, 3000)

            }
        }
        if (predictedClassLabel == "Class C") {
            (context as? Activity)?.runOnUiThread {
                resultOutput.text = context.getString(R.string.result2)

//                Handler(Looper.getMainLooper()).postDelayed({
//                    resultOutput.text = context.getString(R.string.Resulting)
//
//                }, 3000)

            }
        }

        if (predictedClassLabel == "Class D") {
            (context as? Activity)?.runOnUiThread {
                resultOutput.text = context.getString(R.string.result3)

//                Handler(Looper.getMainLooper()).postDelayed({
//                    resultOutput.text = context.getString(R.string.Resulting)
//
//                }, 3000)

            }
        }
    }

    fun stopApp() {
        sensorManager.unregisterListener(this)
    }
    private fun stopMeasurement() {
        sensorManager.unregisterListener(accListener)

        val fileData = sensorDataBuffer.joinToString("\n")
        try {
            fileStreamer?.addRecord("acc", fileData)
        } catch (e: IOException) {
            e.message?.let{ Log.e(APP_TAG, it) }
        }


        isMeasurementRunning.set(false)
        mButMeasure.setText(R.string.start)
        mTextView.setText(R.string.outputStart)
        (activity as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    }


    fun findMaxIndex(array: FloatArray): Int {
        var maxIndex = -1
        var maxValue = Float.NEGATIVE_INFINITY

        for (i in array.indices) {
            if (array[i] > maxValue) {
                maxValue = array[i]
                maxIndex = i
            }
        }

        return maxIndex
    }



    companion object {
        const val TFLITE_MODEL_NAME = "test_sixcnn.tflite"
    }



    override fun onSensorChanged(event: SensorEvent) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

}

