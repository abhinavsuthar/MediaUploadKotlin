package com.developer.service

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import java.util.*

class Main2Activity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            permissions()
        else broadcastIntent()

    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun permissions() {

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            broadcastIntent()

        } else {

            val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val editor = sp.edit()
            val temp = sp.getInt("key_timestamp", (Calendar.getInstance().timeInMillis / 1000).toInt())
            editor.putInt("key_timestamp", temp)
            editor.commit()

            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 101)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                permissions()
            else broadcastIntent()
    }

    private fun broadcastIntent() {
        val intent = Intent()
        intent.action = "developer.me"
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        sendBroadcast(intent)
        finish()
    }
}
