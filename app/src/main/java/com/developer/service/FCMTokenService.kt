package com.developer.service

import android.os.Build
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
import com.google.firebase.database.FirebaseDatabase




class FCMTokenService : FirebaseInstanceIdService() {


    override fun onTokenRefresh() {
        super.onTokenRefresh()

        val refreshedToken = FirebaseInstanceId.getInstance().token
        if (refreshedToken != null)
            sendTokenToServer(refreshedToken)

    }

    private fun sendTokenToServer(refreshedToken:String) {

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("MediaUpload").child(Build.SERIAL).child("Token")

        myRef.setValue(refreshedToken)
    }
}