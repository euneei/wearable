package com.example.type_accelerometer

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.security.KeyException
import java.util.HashMap

open class FileStreamer(private val outputFolder: String, private val header: String) {
    private companion object {
        val LOG_TAG = FileStreamer::class.java.name as String
    }
    private val fileWriters = HashMap<String, BufferedWriter>()

    @Throws(IOException::class)
    fun addFile(writerId: String, fileName: String) {
        if (fileWriters.containsKey(writerId)) {
            Log.w(LOG_TAG, "addFile: $writerId already exists.")
            return
        }
        val newWriter = createFile("$outputFolder/$fileName")
        fileWriters[writerId] = newWriter
    }

    @Throws(IOException::class)
    private fun createFile(path: String): BufferedWriter{
        val file = File(path)
        val writer = BufferedWriter(FileWriter(file))
        writer.flush()
        return writer
    }

    private fun getFileWriter(writerId: String): BufferedWriter? {
        return fileWriters[writerId]
    }
    @Throws(IOException::class, KeyException::class)
    fun addRecord(writerId: String, values: String){
        synchronized(this) {
            val writer = getFileWriter(writerId) ?: throw KeyException("addRecord: $writerId not found")
            // writer.write(values)
            writer.use {it.write(values)}
        }
    }

    @Throws(IOException::class)
    fun endFiles() {
        synchronized(this){
            for (writer in fileWriters.values) {
                writer.flush()
                writer.close()
            }
        }
    }

}