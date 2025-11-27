package com.example.face_verification_app

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.example.neurotecsdklibrary.NeurotecLicenseHelper
import com.neurotec.lang.NCore
import com.neurotec.licensing.NLicenseManager
import com.neurotec.plugins.NDataFileManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltAndroidApp
class FaceMatchApplication : Application() {

    companion object {
        private const val TAG = "FaceMatchApplication"
        @Volatile
        var areLicensesActivated = false
            private set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        try {
            NLicenseManager.setTrialMode(true)
            Log.d("NeurotecLicense", "Trial mode enabled")
        } catch (e: Exception) {
            Log.e("NeurotecLicense", "Failed to set trial mode", e)
        }

        try {
            NCore.setContext(this)
            NDataFileManager.getInstance().addFromDirectory("data", false)
        }catch (e: Exception){
            Log.e(TAG, "Failed to set NCore context", e)
        }

        try {
//                try {
//                    NDataFileManager.getInstance().addFromDirectory("data", false)
//                    Log.d(TAG, "Neurotec model files added")
//                } catch (ex: Exception) {
//                    Log.e(TAG, "Failed to add data files", ex)
//                }

        areLicensesActivated = NeurotecLicenseHelper.obtain(this)
        if (areLicensesActivated) {
            Log.d(TAG, " Licenses activated successfully")
        } else {
            Log.e(TAG, " License activation FAILED")
        }
        } catch (e: Exception) {
            Log.e(TAG, "Error doing background Neurotec initialization", e)
        }

    }

    override fun onTerminate() {
        super.onTerminate()
        NeurotecLicenseHelper.release()
    }

}