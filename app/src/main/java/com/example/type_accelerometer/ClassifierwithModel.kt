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

//                try {
//                    fileStreamer?.addRecord("acc", dataString)
//                } catch (e: IOException) {
//                    Log.d(APP_TAG, "data in file")
//                }

                /*
                if(accelerometerDataList.size >= 50){
                    val recentData = accelerometerDataList.takeLast(50)
                    val recentChangeSum = calculateRecentChangeSum(recentData)

                    if (recentChangeSum <= 5) {
                        accelerometerDataList = accelerometerDataList.dropLast(50).toMutableList()
                    } else if (accelerometerDataList.size >=300){
                        inferFromData(accelerometerDataList.takeLast(300))
                        accelerometerDataList.clear()

                    }
                }
                */

                processData()

            } else {
                Log.i(APP_TAG, "onDataReceived List is zero")
            }
            // processData()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.d("Sensor", "Accuracy changed: $accuracy")

        }

        fun checkProcessData() {

            if (accelerometerDataList.size >= 300) {
                inferFromData(accelerometerDataList.takeLast(300))
            }
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

// 이 함수는 아마 삭제해도 될 듯
//        fun inferFromData(dataList: List<Float>) {
//
//            try {
//                val result = classifyexample(context, dataList)
//                Log.d(APP_TAG_TWO, "result:" + result[0].toString())
//                Log.d(APP_TAG_TWO, "result:" + result[1].toString())
//                Log.d(APP_TAG_TWO, "result:" + result[2].toString())
//
//
//
//                showResults(result)
//                Log.d(APP_TAG, "showResults 호출")
//            } catch (e: IOException) {
//                Log.e(APP_TAG, "Error in processData: ${e.message}", e)
//            } catch (e: KeyException) {
//                Log.e(APP_TAG, "Error in processData: ${e.message}", e)
//            }
//
//            repeat(300) { accelerometerDataList.removeAt(0) }
//        }
        var lastInferenceTime = System.currentTimeMillis()
        var inferenceCount = 0
        var inferenceActive = false

        fun processData() {

            /*

            if (accelerometerDataList.isNotEmpty()) {
                val accMin = accelerometerDataList.minOrNull()!!
                val accMax = accelerometerDataList.maxOrNull()!!
                var accDataList =
                    accelerometerDataList.map { (it - accMin) / (accMax - accMin) }

                Log.d(APP_TAG, "processData")
                Log.d(APP_TAG, "Processing data: ${accelerometerDataList.joinToString()}")

                // 데이터 개수 299로 맞추기, common math interpolation
                when {
                    accDataList.size < 299 -> {
                        interpolateData(accelerometerDataList,299)
                        Log.d(APP_TAG, "Data interpolated to 299")
                    }
                    accDataList.size > 299 -> {
                        accDataList = accDataList.take(299)
                        Log.d(APP_TAG, "Data reduced to 299")
                    }
                    else -> {
                        Log.d(APP_TAG, "Data size is exactly 299")
                    }
                }

                try {
                    val result = classify(accDataList)
                    Log.d(APP_TAG, result[0].toString())
                    Log.d(APP_TAG, result[1].toString())
                    Log.d(APP_TAG, result[2].toString())

                    showResults(result)
                } catch (e: IOException) {
                    Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                } catch (e: KeyException) {
                    Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                }


                // 리스너 해제
                sensorManager.unregisterListener(this)
                Log.d(APP_TAG, "sensorManager.unregisterListener")

                accelerometerDataList.clear()
                Log.d(APP_TAG, "accelerometerDataList.clear()")

                isMeasurementRunning.set(false)
                Log.d(APP_TAG, "isMeasurementRunning.set(false)")

            } else {
                Log.i(APP_TAG, "onSensorChanged is zero")
            }

             */

            Log.d(APP_TAG, "processData 함수 호출됨")
            /*
            val changeList = mutableListOf<Double>()  // 변화량의 합 리스트


            if(accelerometerDataList.size >= 50) {
                val recentData = accelerometerDataList.takeLast(50)
                var totalChange = 0.0

                for (i in 1 until recentData.size) {
                    totalChange += abs(recentData[i] - recentData[i-1])
                }

                if(totalChange > 5) {

                }
            }




            for (i in 1 until changeList.size) {
                val diff = abs(changeList[i] - changeList[i - 1])
                if (diff > 0) {
                    // 임계값을 초과하는 경우, 추론을 시작
                    val inferenceData = accelerometerDataList.take(300)
                    try {
                        val result = classifyexample(context, inferenceData)
                        Log.d(APP_TAG, "Change difference: $diff, Starting inference")
                        Log.d(APP_TAG, "Result: ${result.joinToString()}")
                        showResults(result)
                        break // 추론을 시작한 후 반복문을 탈출
                    } catch (e: Exception) {
                        Log.e(APP_TAG, "Error in processData: ${e.message}")
                    }
                }
            }




            while (accelerometerDataList.size >= 300) {
                Log.d(APP_TAG, "accelerometerDatalist.size 300 이상")
                val inferenceData = accelerometerDataList.take(300)
                val average = inferenceData.average()

                if (average <= 9.8)
                {
                    Log.d(APP_TAG, "Average is under 8")
                    repeat(300) {accelerometerDataList.removeAt(0)}

                    return
                }

                else{
                    try {
                        val result = classify(inferenceData)
                        Log.d(APP_TAG_TWO, "result:"+result[0].toString())
                        Log.d(APP_TAG_TWO, "result:"+result[1].toString())
                        Log.d(APP_TAG_TWO, "result:"+result[2].toString())



                        showResults(result)
                        Log.d(APP_TAG, "showResults 호출")
                    } catch (e: IOException) {
                        Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                    } catch (e: KeyException) {
                        Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                    }

                    repeat(300) {accelerometerDataList.removeAt(0)}
                }

            }




            repeat(300) { accelerometerDataList.removeAt(0)}
        }

 */

            /*
            while (accelerometerDataList.size >= 300) {
                Log.d(APP_TAG, "accelerometerDatalist.size 300 이상")
                val inferenceData = accelerometerDataList.take(300)

                try {
                    // val result = classify(inferenceData)

//
//                    Log.d(APP_TAG_TWO, "result:"+result[0].toString())
//                    Log.d(APP_TAG_TWO, "result:"+result[1].toString())
//                    Log.d(APP_TAG_TWO, "result:"+result[2].toString())
//
                    val result2 = classifyexample(context, inferenceData)
                    //val result2 = classifyexample(context)

                    showResults(result2)
                    Log.d(APP_TAG, "showResults 호출")
                } catch (e: IOException) {
                    Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                } catch (e: KeyException) {
                    Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                }

                repeat(300) { accelerometerDataList.removeAt(0) }
            }
        }

 */


            /*
            Log.d(APP_TAG, "processData 함수 호출됨")

            var startIndex = 0  // 추론을 시작할 인덱스
            var isInferencing = false  // 임계값을 한 번 넘었는지 추적

            // 데이터가 최소 300개 이상 있어야 추론이 가능함
            while (startIndex + 50 <= accelerometerDataList.size) {
                if (!isInferencing) {  // 아직 임계값을 넘지 않은 경우
                    // 최근 50개 데이터의 합을 계산
                    val recentData = accelerometerDataList.subList(startIndex, startIndex + 50)
                    val recentSum = recentData.sum()

                    // 이전 50개 데이터의 합을 계산 (이전 구간이 존재하는 경우만)
                    val previousSum = if (startIndex >= 50) {
                        val previousData = accelerometerDataList.subList(startIndex - 50, startIndex)
                        previousData.sum()
                    } else {
                        0f
                    }

                    // 두 구간의 합계 차이를 계산하여 임계값을 초과하는지 확인
                    val diff = abs(recentSum - previousSum)
                    if (diff < 500) {
                        // 임계값 미만이면 다음 50개 구간을 확인
                        startIndex += 50
                        continue
                    }

                    // 임계값 초과로 추론을 시작하고 플래그를 설정
                    Log.d(APP_TAG, "임계값 초과로 추론 시작, startIndex: $startIndex")
                    isInferencing = true
                }

                // 임계값을 초과한 경우 혹은 이미 추론 중인 경우 300개 데이터 사용
                if (startIndex + 300 <= accelerometerDataList.size) {
                    val inferenceData = accelerometerDataList.subList(startIndex, startIndex + 300)

                    try {
                        // 추론 함수 호출
                        val result = classifyexample(context, inferenceData)
                        showResults(result)
                        Log.d(APP_TAG, "showResults 호출")
                    } catch (e: IOException) {
                        Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                    } catch (e: KeyException) {
                        Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                    }

                    // 다음 추론 시작 인덱스를 150개 증가하여 오버랩된 추론 준비
                    startIndex += 150
                } else {
                    // 데이터가 300개보다 적으면 종료
                    break
                }
            }

            if (startIndex >= 150) {
                accelerometerDataList.subList(0, startIndex - 150).clear()
            }
        }



             */

            /*
            if (accelerometerDataList.size >= 150 && !isInferring) {
                val recentData = accelerometerDataList.takeLast(150)


                val recentChangeSum = calculateRecentChangeSum(recentData)

                // 50개 버리고 다시

                /*
                if (recentChangeSum <= 5) {
                    accelerometerDataList = accelerometerDataList.drop(50).toMutableList()
                } else if (accelerometerDataList.size >= 300) {
                    val inferenceData = accelerometerDataList.take(300)

                    try {
                        // 추론 함수 호출
                        val result = classifyexample(context, inferenceData)
                        showResults(result)
                        Log.d(APP_TAG, "showResults 호출")
                    } catch (e: IOException) {
                        Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                    } catch (e: KeyException) {
                        Log.e(APP_TAG, "Error in processData: ${e.message}", e)
                    }

                    accelerometerDataList.clear()
                }

                 */

                if(recentChangeSum <= 5) {
                    accelerometerDataList = accelerometerDataList.drop(50).toMutableList()

                } else {
                    isInferring = true
                }
            }
            if(isInferring && accelerometerDataList.size >= 300){
                val inferenceData = accelerometerDataList.take(300)
                inferFromData(inferenceData)
                accelerometerDataList = accelerometerDataList.takeLast(150).toMutableList()
            }

             */

            // 6초마다 추론
            // IntervalTime
            // resultOutput.text = context.getString(R.string.nothing)
            //                    Log.i(APP_TAG_TWO, "changesum is less than 3")
            //                    accelerometerDataList.clear()

            /*
            if(accelerometerDataList.size >= 150) {
                val recentData = accelerometerDataList.takeLast(150)
                val recentChangeSum = calculateRecentChangeSum(recentData) // 변화량 계산함

                if (recentChangeSum >= 3) {
                    if (accelerometerDataList.size >= 300) {
                        val inferenceData = accelerometerDataList.takeLast(300)
                        inferFromData(inferenceData)
                        inferenceCount += 1

                        if (inferenceCount < 6) {
                            accelerometerDataList =
                                accelerometerDataList.takeLast(150).toMutableList()
                        } else {
                            resultOutput.text = context.getString(R.string.Resulting)
                            accelerometerDataList.clear()
                            inferenceCount = 0
                        }
                    }
                } else {
                    resultOutput.text = context.getString(R.string.nothing)
                    accelerometerDataList.clear()

                }
            }

             */

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


        /*
        fun processData() {
            val changeList = mutableListOf<Double>()
            var isInferencing = false
            var startIndex = 0

            while (startIndex + 300 <= accelerometerDataList.size) {
                if (!isInferencing) {  // 아직 추론을 시작하지 않은 경우
                    if (accelerometerDataList.size >= 100) {
                        // 최근 50개 구간과 그 이전 50개 구간의 합을 비교
                        val recentData = accelerometerDataList.takeLast(50)
                        val previousData = accelerometerDataList.takeLast(100).take(50)

                        val recentSum = recentData.sum()
                        val previousSum = previousData.sum()

                        // 두 구간의 합계 차이 계산
                        val diff = abs(recentSum - previousSum)

                        // 합계 차이가 임계값을 초과하는 경우 추론 시작
                        if (diff >= 10) {
                            isInferencing = true  // 추론을 시작하면 플래그를 true로 설정
                            Log.d(APP_TAG, "Starting inference due to sum difference >= 10")
                        } else {
                            startIndex += 1  // 변화량 체크만 하고 계속 진행
                            continue
                        }
                    } else {
                        break  // 데이터가 100개보다 적으면 변화량 체크할 수 없음
                    }
                }

                // 추론을 시작한 경우 300개 데이터 사용
                val inferenceData = accelerometerDataList.subList(startIndex, startIndex + 300)

                try {
                    // 추론 함수 호출
                    val result = classify(inferenceData)
                    Log.d(APP_TAG, "Result: ${result.joinToString()}")
                    showResults(result)

                    // 다음 데이터 시작 인덱스를 150 증가시켜 오버랩된 추론 준비
                    startIndex += 150
                } catch (e: Exception) {
                    Log.e(APP_TAG, "Error in processData: ${e.message}")
                    break  // 예외 발생 시 반복문을 탈출
                }
            }
        }
    }

         */

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
        /*
        else {
            sensorManager.unregisterListener(accListener)

            isMeasurementRunning.set(false)
            mButMeasure.setText(R.string.start)
            mTextView.setText(R.string.outputStart)
            (activity as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        */
    }


    /*
    fun interpolateData(dataList: List<Float>, targetSize: Int): List<Float> {

        try {
            val interpolator: UnivariateInterpolator = LinearInterpolator()
            val xVals = DoubleArray(dataList.size) { it.toDouble() }
            val yVals = dataList.map { it.toDouble() }.toDoubleArray()

            val function = interpolator.interpolate(xVals, yVals)
            val stepSize = (dataList.size-1).toDouble() / (targetSize - 1)
            return (0 until targetSize).map { index ->
                val xValue = min(index*stepSize, xVals.last())
                function.value(xValue).toFloat()
            }
        } catch (e: Exception){
            Log.e(APP_TAG, "Error in interpolateData: ${e.message}", e)
            return emptyList()
        }

    }
    */
    fun interpolateData(dataList: List<Float>, targetSize: Int): List<Float> {
        try {
            val interpolator: UnivariateInterpolator = LinearInterpolator()
            val xVals = DoubleArray(dataList.size) { it.toDouble() }
            val yVals = dataList.map { it.toDouble() }.toDoubleArray()

            val function = interpolator.interpolate(xVals, yVals)
            val stepSize = (dataList.size - 1).toDouble() / (targetSize - 1)  // 298-1/299-1=297/298=0.9966
            val interpolatedData = ArrayList<Float>(targetSize)

            for (index in 0 until targetSize) {
                val xValue = if (index == targetSize - 1) xVals.last() else min(index * stepSize, xVals.last())
                // val xValue = min(index * stepSize, xVals.last())
                val interpolatedValue = function.value(xValue).toFloat()
                interpolatedData.add(interpolatedValue)
                Log.d(APP_TAG, "Interpolated data at index $index: $interpolatedValue")
            }

            return interpolatedData
        } catch (e: Exception) {
            Log.e(APP_TAG, "Error in interpolateData: ${e.message}", e)
            return emptyList()
        }
    }


    private fun getInterpreter(
        context: Context,
        modelName: String,
        tfLiteOptions: Interpreter.Options
    ): Interpreter {
        return Interpreter(FileUtil.loadMappedFile(context, modelName), tfLiteOptions)
    }

/*
    fun classify(accDataList: List<Float>): FloatArray {
        val tfLiteOptions = Interpreter.Options()
        interpreter = getInterpreter(context, TFLITE_MODEL_NAME, tfLiteOptions)


        val inputBuffer = ByteBuffer.allocateDirect(accDataList.size * 4) // 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder())

        Log.d(APP_TAG, "for value in accDataList")
        for (value in accDataList) {
            inputBuffer.putFloat(value)
        }

        inputBuffer.rewind()

        val outputBuffer =
            ByteBuffer.allocateDirect(4 * 3) // Adjust the size based on the model's output

        Log.d(APP_TAG, "interpreter.run")
        interpreter?.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        Log.d(APP_TAG_TWO, "outputbuffer: " + outputBuffer.getFloat(0))
        Log.d(APP_TAG_TWO, "outputbuffer2: " + outputBuffer.getFloat(4))
        Log.d(APP_TAG_TWO, "outputbuffer3: " + outputBuffer.getFloat(8))


//        val inputTensor = interpreter?.getInputTensor(0) // 첫 번째 입력 텐서를 가져옴
//        val outputTensor = interpreter?.getOutputTensor(0) // 첫 번째 출력 텐서를 가져옴
//        if (inputTensor != null) {
//            Log.d(APP_TAG_TWO, "Input tensor data type: ${inputTensor.dataType()}")
//        }
//        if (outputTensor != null) {
//            Log.d(APP_TAG_TWO, "Output tensor data type: ${outputTensor.dataType()}")
//        }

        return floatArrayOf(
            outputBuffer.getFloat(0),
            outputBuffer.getFloat(4),
            outputBuffer.getFloat(8)
        )

        //return floatArrayOf(0.0f)
    }

 */

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


    /* 리스트 테스트 코드
    fun classifyexample(context: Context): FloatArray {
        val model = TestFourcnn.newInstance(context)

        val accDataList = listOf(25.335905,24.958415,24.921854,26.171431,26.640135,24.533703,20.688007,16.853436,13.139688,11.25297,9.751083,8.616461,7.479769,6.668352,6.214933,5.771364,5.324212,5.068552,4.786464,4.911644,5.710488,7.16931,9.699838,13.648392,19.377428,26.596086,31.628273,34.456403,34.537655,32.90528,29.672378,28.294641,25.977523,20.887832,17.031845,14.75843,13.642517,13.287054,13.291718,13.226847,13.022798,12.85358,12.547742,12.415268,13.523371,15.420029,17.183961,19.4983,21.966829,25.395685,25.351746,22.766373,23.69596,26.806953,28.282377,27.945619,25.953888,23.060718,20.240175,18.177541,16.582629,13.976026,11.614186,9.712915,7.910914,6.142029,5.523798,5.383642,5.498189,5.572974,5.616658,5.56284,6.066041,7.434251,9.509692,11.221938,15.072665,22.468734,36.003133,39.540072,40.338045,38.590571,33.967486,28.228549,22.193946,17.294421,14.864715,12.863991,12.049466,11.554232,10.92527,9.859567,9.909487,10.250901,11.013772,10.955158,11.141946,12.161386,15.323447,18.362281,21.683637,24.380608,26.736548,27.730692,26.719805,28.377741,28.31912,28.254436,24.824589,19.681128,15.501607,12.4419,10.209889,8.149551,6.407658,5.348998,5.003761,4.680405,4.829663,4.602006,4.093557,3.971458,4.369804,4.679844,5.300542,7.13015,10.244467,15.635317,23.831799,29.537477,29.870481,30.329623,28.494311,27.651906,24.21481,20.732327,17.638266,15.295448,13.41987,12.337541,11.157828,10.105554,9.264813,8.72563,8.31428,8.427789,8.225438,8.79946,9.954953,11.626605,14.602783,17.329921,23.892389,33.173427,26.054183,26.981231,25.223052,26.084778,25.514825,22.809078,19.398683,15.349739,11.999698,9.24107,7.395759,5.968969,4.991459,4.445535,4.364096,4.62567,4.740656,4.344801,3.983866,3.93019,3.802259,3.742294,4.435716,5.836986,8.28203,11.620243,24.334693,37.465067,30.073688,24.297294,24.648422,23.480319,20.774529,19.435944,17.741134,15.365785,13.161956,11.568046,12.722783,15.413154,17.485231,18.9219,20.154326,19.701476,19.78818,21.034139,23.168761,24.042145,23.995459,25.066669,25.691857,26.051887,24.599449,20.272093,24.45006,21.085483,11.71909,8.245275,7.362706,5.910377,4.186748,3.646226,10.472422,5.213603,5.889952,18.407928,12.758527,8.757598,6.327963,10.448922,11.462075,13.425654,10.604701,9.126454,8.524206,6.418761,7.667426,9.697025,13.304546,16.389008,20.153662,21.121944,26.245584,24.67257,20.874071,17.100083,16.505772,17.832294,20.219517,21.596148,19.248554,15.527822,12.466373,11.421078,15.297624,19.124754,19.65674,24.007579,27.556127,27.707739,24.333571,20.979683,19.641095,19.157219,18.279804,14.820215,10.70926,7.452825,5.553029,4.493144,3.993897,3.457457,2.665835,2.730685,2.496837,3.114405,3.746585,5.40109,8.760157,10.879786,11.709827,13.644038,17.076275,19.591443,20.10419,18.91798,18.949937,20.256047,20.538786,19.945091,18.861974,17.054821,15.047295,13.423248,12.583053,11.5389,10.565295,9.904809,9.453264,10.241627,13.114888,14.332404,14.950428,15.678272,16.591391,17.904884
        )  // 실제 데이터 수는 모델 입력에 맞추어 조정해야 함


        val byteBuffer = ByteBuffer.allocateDirect(accDataList.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        accDataList.forEach {
            byteBuffer.putFloat(it.toFloat())
        }

        byteBuffer.rewind() // 버퍼 포인터를 처음으로 되돌림


        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1,300,1), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)

        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        val resultData = outputFeature0.floatArray
        resultData.forEachIndexed{index, value -> println("Output $index: $value") }

        model.close()
        Log.d(APP_TAG_TWO, "output: " + resultData[0])
        Log.d(APP_TAG_TWO, "outputbuffer2: " + resultData[1])
        Log.d(APP_TAG_TWO, "outputbuffer3: " + resultData[2])


        return floatArrayOf(
            resultData[0],
            resultData[1],
            resultData[2]
        )

    }

     */


    private fun showResults(inferenceResult: FloatArray) {
        /*
        val classLabels = arrayOf("Class A", "Class B", "Class C")

        val maxIndex = findMaxIndex(inferenceResult)
        val predictedClassLabel = classLabels[maxIndex]

        if (predictedClassLabel == "Class A") {
            (context as? Activity)?.runOnUiThread {
                resultOutput.text = context.getString(R.string.result0)
            }
        }
        if (predictedClassLabel == "Class B") {
            (context as? Activity)?.runOnUiThread {
                resultOutput.text = context.getString(R.string.result1)
            }
        }
        if (predictedClassLabel == "Class C") {
            (context as? Activity)?.runOnUiThread {
                resultOutput.text = context.getString(R.string.result2)
            }
        }

         */
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


    fun startClassifier() {

        Log.i(APP_TAG, "startClassifier called")
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        Log.i(APP_TAG, "startClassifier called22")

        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        Log.i(APP_TAG, "startClassifier called33")




        sensorManager.registerListener(accListener, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        Log.i(APP_TAG, "startClassifier called44")

        resultOutput.text = context.getString(R.string.Resulting)
        Log.i(APP_TAG, "startClassifier called55")

        (activity as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        isMeasurementRunning.set(true)
        Log.i(APP_TAG, "startClassifier called66")

        // 3초 후에 측정 종료
        /*
        Handler(Looper.getMainLooper()).postDelayed({
            accListener.processData()
            isMeasurementRunning.set(false)

        }, dataInterval) // 3.5초 후 측정 종료  -> 3(3000)초에 298보다 조금 들어와서 299보다 적게 들어올 경우, 보간을 해도 298개의 데이터가 수집이 되는 현상 발견, 따라서 무조건 조금 오래 받아서 잘랐음. 그런데 잘린 0.5사이의 데이터가 위험상황에 영향을 미칠까?
        Log.i(APP_TAG, "startClassifier called77")
*/




        Log.i(APP_TAG, "UI state updated successfully")


    }

    companion object {
        const val TFLITE_MODEL_NAME = "test_sixcnn.tflite"
    }



    override fun onSensorChanged(event: SensorEvent) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

}

