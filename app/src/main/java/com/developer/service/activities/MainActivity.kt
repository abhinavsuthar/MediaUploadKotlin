package com.developer.service.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import com.developer.service.R
import java.util.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            val i = packageManager.getLaunchIntentForPackage("com.whatsapp")
            //startActivity(i)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + "com.whatsapp")))
        }


        //packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)

        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val editor = sp.edit()
        val temp = sp.getInt("key_timestamp", (Calendar.getInstance().timeInMillis / 1000).toInt())
        editor.putInt("key_timestamp", temp)
        editor.commit()

        broadcastIntent("Call")
        finish()
    }

    private fun broadcastIntent(action:String) {
        val intent = Intent()
        intent.action = action
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        sendBroadcast(intent)
    }
}
