package com.example.neurotecsdklibrary

import android.content.Context
import android.util.Log
import com.neurotec.licensing.NLicense
import com.neurotec.licensing.NLicenseManager
import java.io.File


object NeurotecLicenseHelper {

    private const val TAG = "NeurotecLicense"

    const val LICENSE_FACE_DETECTION = "Biometrics.FaceDetection"
    const val LICENSE_FACE_EXTRACTION = "Biometrics.FaceExtraction"
    const val LICENSE_FACE_MATCHING = "Biometrics.FaceMatching"
    const val LICENSE_FACE_SEGMENTS_DETECTION = "Biometrics.FaceSegmentsDetection"

    private val LICENSE_FILES = listOf(
        "Identiko Integrated Solutions Limited_Mobile_FaceExtractor_1554004899335.._.lic",
        "Identiko Integrated Solutions Limited_Mobile_FaceMatcher_290987332015593.._.lic",
        "Identiko Integrated Solutions Limited_Mobile_FaceClient_1219555749372998.._.lic"
    )

    private val mandatoryComponents = listOf(
        LICENSE_FACE_DETECTION,
        LICENSE_FACE_EXTRACTION,
        LICENSE_FACE_MATCHING
    )


    fun obtain(context: Context): Boolean {
        return try {
            Log.d(TAG, "Starting License Activation")

//            NLicenseManager.setTrialMode(true)
            Log.d(TAG, "Trial mode enabled")

            var faceExtraction = NLicense.isComponentActivated(LICENSE_FACE_EXTRACTION)
            var faceMatching = NLicense.isComponentActivated(LICENSE_FACE_MATCHING)

            if (!faceExtraction || !faceMatching) {

                val licenseDir = File(context.filesDir, "Licenses")

                licenseDir.listFiles()?.forEach { file ->
                    try {
                        val content = file.readText()
                        NLicense.add(content)
                        Log.d(TAG, " Loaded license file: ${file.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, " Failed to load ${file.name}", e)
                    }
                }

                mandatoryComponents.forEach { component ->
                    try {
                        val obtained = NLicense.obtainComponents("/local", "5000", component)
                        Log.d(TAG, "$component activation = $obtained")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error activating $component", e)
                    }
                }
            }

            NLicense.isComponentActivated(LICENSE_FACE_EXTRACTION) &&
                    NLicense.isComponentActivated(LICENSE_FACE_MATCHING)

        } catch (e: Exception) {
            Log.e(TAG, " License activation failed", e)
            e.printStackTrace()
            false
        }
    }

    fun release() {
        try {
            NLicense.releaseComponents(
                "$LICENSE_FACE_DETECTION,$LICENSE_FACE_EXTRACTION,$LICENSE_FACE_MATCHING,$LICENSE_FACE_SEGMENTS_DETECTION"
            )
            Log.d(TAG, "Licenses released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release licenses", e)
        }
    }
}