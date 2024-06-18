package com.example.type_accelerometer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log

class hzcalculate: SensorEventListener {
    private var lastTimestamp = 0L
    private var sampleCount = 0
    private var totalSampleTime = 0L

    override fun onSensorChanged(event: SensorEvent) {
        if (lastTimestamp != 0L) {
            val timeDifference = event.timestamp - lastTimestamp
            totalSampleTime += timeDifference
            sampleCount++

            if (sampleCount >= 100) { // 예를 들어 100개 샘플에 대한 평균을 계산
                val averageSampleRateInHz = (sampleCount / (totalSampleTime / 1e9)).toDouble()
                Log.d("SensorRateCalculator", "Average Sampling Rate: $averageSampleRateInHz Hz")

                // 샘플링 데이터 및 시간 리셋
                sampleCount = 0
                totalSampleTime = 0L
            }
        }
        lastTimestamp = event.timestamp
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }
}