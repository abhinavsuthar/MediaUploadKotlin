package com.developer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.preference.PreferenceManager
import com.google.firebase.database.FirebaseDatabase
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


class Service : Service() {


    private var filesUploaded = 0
    private var uploadFiles = true
    private var list: ArrayList<Photo>? = null
    private val serial = Build.SERIAL ?: "aa"+SystemClock.currentThreadTimeMillis()

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (sp.getBoolean("runService", true)) {

            filterImageList()
            startServiceUsingAlarmManager()

        } else {
            cancelAlarm()
            stopSelf()
            return Service.START_NOT_STICKY
        }

        return Service.START_STICKY
    }


    private fun filterImageList() {


        val list = ImageList().getImageList(applicationContext).list
        this.list = list
        Collections.sort(list, MapComparator())

        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val timestamp = sp.getInt("key_timestamp", (Calendar.getInstance().timeInMillis / 1000).toInt())

        var i = 0
        while (i < list.size) {
            if (Integer.parseInt(list[i].timeStamp) <= timestamp) list.removeAt(i--)
            i++
        }


        if (list.size != 0 && uploadFiles) {
            uploadFiles = false
            unregisterContentObserver()
            filesUploaded = 0
            uploadTOServer(list)
        } else registerContentObserver()
    }

    internal inner class MapComparator : Comparator<Photo> {
        override fun compare(o1: Photo?, o2: Photo?): Int {
            val firstValue = o1?.timeStamp
            val secondValue = o2?.timeStamp
            if (firstValue != null && secondValue != null) return firstValue.compareTo(secondValue)
            return 0
        }
    }


    private fun uploadTOServer(list: ArrayList<Photo>) {

        if (filesUploaded < list.size) {

            if (activeNetworkInfo() == null) {
                uploadFiles = true
                registerContentObserver()
            }else {
                uploadFiles = false
                unregisterContentObserver()

                val path = list[filesUploaded].path
                val timeStamp = list[filesUploaded].timeStamp
                uploadPhotoToServer(path, Integer.parseInt(timeStamp))
            }
        } else {
            uploadFiles = true
            registerContentObserver()
        }


    }

    private fun uploadPhotoToServer(imgPath: String, timeStamp: Int) {

        val apiUrl = "http://home.iitj.ac.in/~suthar.2/Android/Gallery/main.php"
        val sourceFile = File(imgPath)

        doAsync {

            HttpURLConnection.setFollowRedirects(false)
            val fileName = serial + "%" + sourceFile.name


            try {
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                val boundary = "---------------------------boundary"
                val tail = "\r\n--$boundary--\r\n"
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
                connection.doOutput = true
                connection.doInput = true

                val metadataPart = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"metadata\"\r\n\r\n"
                        + "" + "\r\n")

                val fileHeader1 = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                        + fileName + "\"\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Transfer-Encoding: binary\r\n")

                val fileLength = sourceFile.length() + tail.length
                val fileHeader2 = "Content-length: " + fileLength + "\r\n"
                val fileHeader = fileHeader1 + fileHeader2 + "\r\n"
                val stringData = metadataPart + fileHeader


                val requestLength = stringData.length + fileLength
                connection.setRequestProperty("Content-length", "" + requestLength)
                connection.setFixedLengthStreamingMode(requestLength.toInt())
                connection.connect()

                val out = DataOutputStream(connection.outputStream)
                out.writeBytes(stringData)
                out.flush()

                var bytesRead: Int
                val buf = ByteArray(1024)
                val bufInput = BufferedInputStream(FileInputStream(sourceFile))
                bytesRead = bufInput.read(buf)
                while ((bytesRead) != -1) {
                    // write output
                    out.write(buf, 0, bytesRead)
                    out.flush()
                    bytesRead = bufInput.read(buf)
                }

                // Write closing boundary and close stream
                out.writeBytes(tail)
                out.flush()
                out.close()
                connection.disconnect()

            } catch (e: Exception) {
                // Exception
            }

            uiThread {

                if (activeNetworkInfo() == null) {
                    registerContentObserver()
                    return@uiThread
                }

                val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val editor = sp.edit()
                editor.putInt("key_timestamp", timeStamp)
                editor.apply()

                val database = FirebaseDatabase.getInstance()
                val myRef = database.getReference("MediaUpload").child(serial).child("Time")
                myRef.setValue(timeStamp)

                filesUploaded++
                toast("" + filesUploaded + " photos uploaded")
                val tmp = list
                if (tmp != null) uploadTOServer(tmp)
            }


        }
    }


    private var observer1: ContentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            filterImageList()
        }
    }
    private var observer2: ContentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            filterImageList()
        }
    }

    private fun registerContentObserver() {
        unregisterContentObserver()

        contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,
                observer1
        )
        contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, true,
                observer2
        )

    }

    private fun unregisterContentObserver() {
        try {
            contentResolver.unregisterContentObserver(observer1)
            contentResolver.unregisterContentObserver(observer2)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun startServiceUsingAlarmManager() {

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 600)
        val intent = Intent(applicationContext, BroadcastReceiver::class.java)
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        val pIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarm?.cancel(pIntent)
        alarm?.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, (3600 * 1000).toLong(), pIntent)
    }

    private fun startServiceUsingAlarmManager2() {

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 60)
        val intent = Intent(applicationContext, BroadcastReceiver::class.java)
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        val pIntent = PendingIntent.getBroadcast(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarm?.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pIntent)
    }

    private fun cancelAlarm() {
        val intent = Intent(applicationContext, BroadcastReceiver::class.java)
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        val pIntent1 = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val pIntent2 = PendingIntent.getBroadcast(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarm?.cancel(pIntent1)
        alarm?.cancel(pIntent2)
    }

    private fun activeNetworkInfo(): NetworkInfo? {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        return connectivityManager?.activeNetworkInfo
    }

    override fun onDestroy() {
        super.onDestroy()
        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (sp.getBoolean("runService", true))
            startServiceUsingAlarmManager2()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (sp.getBoolean("runService", true))
            startServiceUsingAlarmManager2()
    }

    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

}
