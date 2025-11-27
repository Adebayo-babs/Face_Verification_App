package com.example.neurotecsdklibrary

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.neurotec.biometrics.NBiometricStatus
import com.neurotec.biometrics.NFace
import com.neurotec.biometrics.NSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.neurotec.biometrics.client.NBiometricClient
import com.neurotec.images.NImage

class NeurotecFaceManager(private val context: Context) {


    companion object {
        private const val TAG = "NeurotecFaceManager"
    }

    private val biometricClient: NBiometricClient by lazy {
        Log.d(TAG, "Creating NBiometricClient...")
        try {
            val client = NBiometricClient()

            // Set quality threshold (lower = more lenient)
            client.facesQualityThreshold = 50

            // Template size
            client.facesTemplateSize = com.neurotec.biometrics.NTemplateSize.LARGE

            client.setUseDeviceManager(true)
            client.initialize()

            Log.d(TAG, " NBiometricClient configured and initialized")
            client
        } catch (e: Exception) {
            Log.e(TAG, " Failed to create NBiometricClient", e)
            throw e
        }
    }


    init {
        Log.d(TAG, "NeurotecFaceManager instance created (client will be initialized on first use)")
    }
    suspend fun extractFaceTemplate(bitmap: Bitmap): Result<NSubject> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== STARTING FACE EXTRACTION ===")
            Log.d(TAG, "Bitmap details: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")

            val processedBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                Log.d(TAG, "Converting bitmap from ${bitmap.config} to ARGB_8888")
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

            val client = biometricClient
            Log.d(TAG, " Biometric client obtained")

            val subject = NSubject()
            val face = NFace()

            Log.d(TAG, "Converting bitmap to NImage...")
            val image = try {
                NImage.fromBitmap(processedBitmap)
            } catch (e: Exception) {
                Log.e(TAG, " Failed to convert bitmap to NImage", e)
                throw e
            }

            Log.d(TAG, " NImage created: ${image.width}x${image.height}")

            face.image = image
            subject.faces.add(face)

            Log.d(TAG, "Calling createTemplate...")
            val status = client.createTemplate(subject)
            Log.d(TAG, "CreateTemplate returned status: $status")

            if (status != NBiometricStatus.OK) {
                // Log more details about the failure
                Log.e(TAG, " Face extraction failed")
                Log.e(TAG, "  Status: $status")
                Log.e(TAG, "  Subject.Status: ${subject.status}")

                // Check if there are any faces detected
                if (subject.faces.size > 0) {
                    val face = subject.faces[0]
                    Log.e(TAG, "  Face.Status: ${face.status}")
                    Log.e(TAG, "  Face objects count: ${face.objects?.size ?: 0}")
                }

                return@withContext Result.failure(Exception("Face extraction failed: $status"))
            }

            Log.d(TAG, "Face extraction successful")
            Log.d(TAG, "  Template size: ${subject.templateBuffer?.size() ?: 0} bytes")
            Result.success(subject)

        } catch (e: Exception) {
            Log.e(TAG, " Exception during face extraction", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun release() {
        try {
            biometricClient.dispose()
            Log.d(TAG, "Biometric client disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing biometric client", e)
        }
    }



}
