package com.example.neurotecsdklibrary

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.common.FaceMatchResult
import com.example.neurotecsdklibrary.data.AppDatabase
import com.example.neurotecsdklibrary.data.FaceTemplateEntity
import com.neurotec.biometrics.NBiometricCaptureOption
import com.neurotec.biometrics.NBiometricOperation
import com.neurotec.biometrics.NBiometricStatus
import com.neurotec.biometrics.NFace
import com.neurotec.biometrics.NSubject
import com.neurotec.biometrics.client.NBiometricClient
import com.neurotec.devices.NCamera
import com.neurotec.devices.NDeviceType
import com.neurotec.images.NImage
import com.neurotec.io.NBuffer
import com.neurotec.plugins.NDataFileManager
import java.util.EnumSet
import java.util.concurrent.Executors


class EnrollFaceViewModel (application: Application) : AndroidViewModel(application) {

    var matchResult by mutableStateOf<FaceMatchResult?>(null)
        private set

    var status by mutableStateOf("")
        private set

    var biometricClient: NBiometricClient? = null
        private set

    var currentSubject: NSubject? = null
        private set

    var capturedFaceBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var nfcFaceBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // Callback when face is captured and ready for matching
    var onFaceCaptured: ((ByteArray, ByteArray) -> Unit)? = null

    // Store NFC data for auto-matching
    private var nfcCardData: Pair<ByteArray, ByteArray>? = null // (faceImage, template)


    var onMatchComplete: ((FaceMatchResult) -> Unit)? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(Looper.getMainLooper())

    fun initialize() {
        executor.execute {
            try {
                NeurotecLicenseHelper.obtain(getApplication())
                main.post {
                    status = "Licenses OK - Initializing"
                    initClient()
//                    startCapture()
                }
            } catch (e: Exception) {
                main.post { status = "License Error: ${e.message}" }
            }
        }
    }

    private fun initClient() {
        try {
            biometricClient = NBiometricClient().apply {
                setFacesDetectProperties(true)
                isUseDeviceManager = true
                deviceManager.deviceTypes = EnumSet.of(NDeviceType.CAMERA)
                initialize()
            }

            val cameras = biometricClient?.deviceManager?.devices
            if (cameras?.isNotEmpty() == true) {
                val camera = cameras[0] as NCamera
                biometricClient?.faceCaptureDevice = camera

                // Automatically start capture after initialization
                main.postDelayed({
                    status = "Ready. Positioning your face..."
                    startAutomaticCapture()
                }, 500)
            } else {
                status = "No camera found"
            }
        } catch (e: Exception) {
            main.post { status = "Camera initialization error: ${e.message}" }
        }
    }

    fun startAutomaticCapture() {
        executor.execute {
            try {
                Log.d("EnrollFace", "Starting automatic capture...")
                main.post { status = "Detecting face..." }

                val subject = NSubject()
                val face = NFace().apply {
                    captureOptions = EnumSet.of(NBiometricCaptureOption.STREAM)
                }

                subject.faces.add(face)
                main.post { currentSubject = subject }

                val task = biometricClient?.createTask(
                    EnumSet.of(NBiometricOperation.CAPTURE, NBiometricOperation.CREATE_TEMPLATE),
                    subject
                )

                task?.let { biometricClient?.performTask(it) }

                val taskStatus = task?.status

                if (taskStatus == NBiometricStatus.OK) {
                    Log.d("EnrollFace", "Face captured successfully!")
                    main.post {
                        status = "Face captured! Processing..."
                        saveAndTriggerMatching(subject)
                    }
                } else {
                    Log.w("EnrollFace", "Capture failed: $taskStatus")
                    main.post {
                        status = "Looking for face... Please position your face in view"
                        // Retry after a short delay
                        main.postDelayed({ startAutomaticCapture() }, 1000)
                    }
                }

                task?.dispose()

            } catch (e: Exception) {
                Log.e("EnrollFace", "Error during capture", e)
                main.post {
                    status = "Capture error. Retrying..."
                    main.postDelayed({ startAutomaticCapture() }, 1500)
                }
            }
        }
    }

    private fun saveAndTriggerMatching(subject: NSubject) {
        try {
            val face = subject.faces[0]
            val imageBytes = face.image?.save()?.toByteArray()
            val templateBytes = subject.templateBuffer?.toByteArray()

            if (imageBytes != null && templateBytes != null) {
                capturedFaceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                executor.execute {
                    // Save to database
                    AppDatabase.getInstance(getApplication())
                        .faceDao()
                        .insert(
                            FaceTemplateEntity(
                                template = templateBytes,
                                image = imageBytes,
                                timestamp = System.currentTimeMillis()
                            )
                        )

                    main.post {
                        status = "Face saved. Starting verification..."

                        // Notify that face is captured
                        onFaceCaptured?.invoke(imageBytes, templateBytes)

                        // If NFC data is already available, automatically match
                        nfcCardData?.let { (nfcImage, _) ->
                            matchWithNFCFace(nfcImage)
                        }
                    }
                }
            } else {
                main.post { status = "Failed to extract face data" }
            }
        } catch (e: Exception) {
            main.post { status = "Save error: ${e.message}" }
        }
    }

    fun setNFCCardData(faceImage: ByteArray) {
        nfcCardData = faceImage to ByteArray(0)
        Log.d("EnrollFace", "NFC data set, will auto-match when face captured")
    }

    private fun saveAndPrepareForMatching(subject: NSubject) {
        try {
            val face = subject.faces[0]
            val imageBytes = face.image?.save()?.toByteArray()
            val templateBytes = subject.templateBuffer?.toByteArray()

            if (imageBytes != null && templateBytes != null) {
                // Convert captured face to bitmap
                capturedFaceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                executor.execute {
                    //Save to database
                    AppDatabase.getInstance(getApplication())
                        .faceDao()
                        .insert(
                            FaceTemplateEntity(
                                template = templateBytes,
                                image = imageBytes,
                                timestamp = System.currentTimeMillis()
                            )
                        )

                    main.post {
                        status = "Capture saved. Ready for matching"
                    }

//                    val dbTemp = AppDatabase.getInstance(getApplication())
//                        .faceDao()
//                        .getAll()
//
//                    val match = performMatching(templateBytes, dbTemp)

//                    main.post {
//                        matchResult = match
//                        status = "Saved to database."
//                    }
                }
            } else {
                main.post { status = "Failed to extract face data" }
            }
        } catch (e: Exception) {
            main.post { status = "Save error: ${e.message}" }
        }
    }

    fun matchWithNFCFace(nfcFaceBytes: ByteArray) {
        executor.execute {
            try {
                main.post { status = "Matching faces..." }

                // Get the most recent captured face from database
                val latestCapture = AppDatabase.getInstance(getApplication())
                    .faceDao()
                    .getLatest()

                if (latestCapture == null) {
                    main.post { status = "No captured face found" }
                    return@execute
                }

                // Convert NFC face bytes to bitmap
                nfcFaceBitmap = BitmapFactory.decodeByteArray(nfcFaceBytes, 0, nfcFaceBytes.size)

                // Create subjects for matching
                val capturedSubject = NSubject()
                capturedSubject.setTemplateBuffer(NBuffer(latestCapture.template))

                val nfcSubject = NSubject()

                // Try to extract template from NFC image
                val nfcFace = NFace()


                val nfcImage = NImage.fromMemory(NBuffer(nfcFaceBytes))
                nfcFace.image = nfcImage
                nfcSubject.faces.add(nfcFace)

                // Create template for NFC face
                val templateTask = biometricClient?.createTask(
                    EnumSet.of(NBiometricOperation.CREATE_TEMPLATE),
                    nfcSubject
                )
                templateTask?.let { biometricClient?.performTask(it) }

                if (nfcSubject.templateBuffer == null) {
                    main.post { status = "Could not create template from NFC face" }
                    templateTask?.dispose()
                    return@execute
                }

                // Perform matching
                main.post { status = "Comparing faces..." }
                val matchStatus = biometricClient?.verify(capturedSubject, nfcSubject)

                val score = if (matchStatus == NBiometricStatus.OK) {
                    capturedSubject.matchingResults?.getOrNull(0)?.score ?: 0
                } else {
                    0
                }

                val result = FaceMatchResult(
                    capturedFace = latestCapture.template,
                    nfcFace = nfcFaceBytes,
                    score = score,
                    threshold = 70
                )

                main.post {
                    matchResult = result
                    status = if (result.isMatch) "Faces Match!" else "Faces Do Not Match"

                    // Notify listener to navigate to result screen
                    onMatchComplete?.invoke(result)
                }

                templateTask?.dispose()

            } catch (e: Exception) {
                Log.e("EnrollFace", "Error during matching", e)
                main.post { status = "Matching error: ${e.message}" }
            }
        }
    }

    fun matchWithNFCTemplate(nfcTemplateBytes: ByteArray, nfcImageBytes: ByteArray? = null) {
        executor.execute {
            try {
                main.post { status = "Matching faces..." }

                val latestCapture = AppDatabase.getInstance(getApplication())
                    .faceDao()
                    .getLatest()

                if (latestCapture == null) {
                    main.post { status = "No captured face found" }
                    return@execute
                }

                if (nfcImageBytes != null) {
                    nfcFaceBitmap = BitmapFactory.decodeByteArray(nfcImageBytes, 0, nfcImageBytes.size)
                }

                val capturedSubject = NSubject()
                capturedSubject.setTemplateBuffer(NBuffer(latestCapture.template))

                val nfcSubject = NSubject()
                nfcSubject.setTemplateBuffer(NBuffer(nfcTemplateBytes))

                val matchStatus = biometricClient?.verify(capturedSubject, nfcSubject)

                val score = if (matchStatus == NBiometricStatus.OK) {
                    capturedSubject.matchingResults?.getOrNull(0)?.score ?: 0
                } else {
                    0
                }

                val result = FaceMatchResult(
                    capturedFace = latestCapture.template,
                    nfcFace = nfcImageBytes,
                    score = score,
                    threshold = 70
                )

                main.post {
                    matchResult = result
                    status = if (result.isMatch) "Faces Match!" else "Faces Do Not Match"
                    onMatchComplete?.invoke(result)
                }

            } catch (e: Exception) {
                Log.e("EnrollFace", "Error during matching", e)
                main.post { status = "Matching error: ${e.message}" }
            }
        }
    }

//    private fun performMatching(
//        capturedTemplate: ByteArray,
//        storedTemplates: List<FaceTemplateEntity>
//    ): FaceMatchResult {
//        var bestScore = 0
//        var bestMatch: FaceTemplateEntity? = null
//
//        val capturedSubject = NSubject()
//        capturedSubject.templateBuffer = capturedTemplate
//
//        val biometricClient = NBiometricClient()
//        biometricClient.initialize()
//
//        for (template in storedTemplates) {
//            val dbSubject = NSubject()
//            dbSubject.templateBuffer = template.template
//
//            val status = biometricClient.verify(capturedSubject, dbSubject)
//            if (status == NBiometricStatus.OK) {
//                val score = capturedSubject.matchingResults[0].score
//                if (score > bestScore) {
//                    bestScore = score
//                    bestMatch = template
//                }
//            }
//        }
//
//        return FaceMatchResult(
//            capturedFace = capturedTemplate,
//            nfcFace = bestMatch?.image,
//            score = bestScore,
//            isMatch = bestScore >= 70
//        )
//    }


    fun verifyFaces(reference: NSubject, candidate: NSubject, callback: (status: NBiometricStatus, score: Int?) -> Unit) {
        executor.execute {
            try {
                val status = biometricClient?.verify(reference, candidate)
                val score = reference.matchingResults?.getOrNull(0)?.score
                main.post { callback(status ?: NBiometricStatus.NONE, score) }
            } catch (e: Exception) {
                main.post { callback(NBiometricStatus.NONE, null) }
            }
        }
    }


    fun reset() {
        matchResult = null
        capturedFaceBitmap = null
        nfcFaceBitmap = null
        status = ""
    }


    override fun onCleared() {
        super.onCleared()
        try {
            biometricClient?.cancel()
            biometricClient?.dispose()
            currentSubject = null
        } catch (e: Exception) {
            // Ignore
        }
        executor.shutdown()
    }


}