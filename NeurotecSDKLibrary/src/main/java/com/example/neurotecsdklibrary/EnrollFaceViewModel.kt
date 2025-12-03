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
import com.neurotec.biometrics.NFAttributes
import com.neurotec.biometrics.NFace
import com.neurotec.biometrics.NSubject
import com.neurotec.biometrics.client.NBiometricClient
import com.neurotec.devices.NCamera
import com.neurotec.devices.NDeviceType
import com.neurotec.images.NImage
import com.neurotec.io.NBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumSet
import java.util.concurrent.Executors

data class FaceDetectionFeedback(
    val lightingStatus: LightingStatus = LightingStatus.UNKNOWN,
    val distanceStatus: DistanceStatus = DistanceStatus.UNKNOWN,
    val positionStatus: PositionStatus = PositionStatus.UNKNOWN,
    val qualityStatus: QualityStatus = QualityStatus.UNKNOWN,
    val overallMessage: String = "Position your face in view"
)

enum class LightingStatus {
    GOOD, TOO_DARK, TOO_BRIGHT, UNKNOWN
}

enum class DistanceStatus {
    GOOD, TOO_FAR, TOO_CLOSE, UNKNOWN
}

enum class PositionStatus {
    CENTERED, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN, UNKNOWN
}

enum class QualityStatus {
    EXCELLENT, GOOD, FAIR, POOR, UNKNOWN
}

class EnrollFaceViewModel (application: Application) : AndroidViewModel(application) {

    var detectionFeedback by mutableStateOf(FaceDetectionFeedback())
        private set

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

    var onFaceCaptured: ((ByteArray, ByteArray) -> Unit)? = null
    var onMatchComplete: ((FaceMatchResult) -> Unit)? = null
    var onFaceDetectedSound: (() -> Unit)? = null

    private var nfcCardData: Pair<ByteArray, ByteArray>? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(Looper.getMainLooper())

    // Add coroutine support for continuous feedback
    private var feedbackMonitorJob: Job? = null
    private val feedbackScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Camera switch
    var isCapturing by mutableStateOf(false)
        private set

    var useNeurotecCamera by mutableStateOf(true)
        private set


    fun initialize() {
        executor.execute {
            try {
                NeurotecLicenseHelper.obtain(getApplication())
                main.post {
                    status = "Licenses OK - Initializing"
                    initClient()
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

                facesQualityThreshold = 50
                facesConfidenceThreshold = 1
                setProperty("Faces.DetectAllFeaturePoints", "false")
                setProperty("Faces.RecognizeExpression", "false")

                initialize()
            }

            val cameras = biometricClient?.deviceManager?.devices
            if (cameras?.isNotEmpty() == true) {
                val camera = cameras[0] as NCamera
                biometricClient?.faceCaptureDevice = camera

                main.postDelayed({
                    status = "Ready. Positioning your face..."
                    startAutomaticCapture()
                }, 200)
            } else {
                status = "No camera found"
            }
        } catch (e: Exception) {
            main.post { status = "Camera initialization error: ${e.message}" }
        }
    }

    fun toggleCameraPreview() {
        useNeurotecCamera = !useNeurotecCamera
        Log.d("EnrollFace", "Camera preview toggled to: ${if (useNeurotecCamera) "Neurotec (Colored)" else "CameraX (Grayscale)"}")
    }


    fun switchCamera(onComplete: ((Boolean) -> Unit)? = null) {
        Log.d("EnrollFace", "switchCamera() called")

        executor.execute {
            try {
                Log.d("EnrollFace", "Executor running for camera switch")

                val currentCamera = biometricClient?.faceCaptureDevice
                Log.d("EnrollFace", "Current camera: ${currentCamera?.displayName}")

                if (currentCamera == null) {
                    Log.e("EnrollFace", "No active camera found")
                    main.post {
                        status = "No active camera"
                        onComplete?.invoke(false)
                    }
                    return@execute
                }

                val devices = biometricClient?.deviceManager?.devices
                Log.d("EnrollFace", "Available devices: ${devices?.size}")

                if (devices.isNullOrEmpty()) {
                    Log.e("EnrollFace", "No devices available")
                    main.post {
                        status = "No cameras available"
                        onComplete?.invoke(false)
                    }
                    return@execute
                }

                // Log all available devices
                devices.forEachIndexed { index, device ->
                    Log.d("EnrollFace", "Device $index: ${device.displayName}, Type: ${device.deviceType}, isCapturing: ${if (device is NCamera) device.isCapturing else "N/A"}")
                }

                // Find ALL cameras first
                val allCameras = devices.filter { it.deviceType.contains(NDeviceType.CAMERA) } as List<NCamera>
                Log.d("EnrollFace", "Total cameras found: ${allCameras.size}")

                if (allCameras.size < 2) {
                    Log.w("EnrollFace", "Only ${allCameras.size} camera(s) available")
                    main.post {
                        status = "Only one camera available"
                        onComplete?.invoke(false)
                    }
                    return@execute
                }

                // Find the next camera (not the current one)
                val currentIndex = allCameras.indexOf(currentCamera)
                val nextIndex = (currentIndex + 1) % allCameras.size
                val nextCamera = allCameras[nextIndex]

                Log.d("EnrollFace", "Current camera index: $currentIndex, Next camera index: $nextIndex")
                Log.d("EnrollFace", "Switching from '${currentCamera.displayName}' to '${nextCamera.displayName}'")

                // Check if currently capturing
                val wasCapturing = currentCamera.isCapturing
                Log.d("EnrollFace", "Was capturing: $wasCapturing")

                // Stop capturing if active
                if (wasCapturing) {
                    main.post {
                        status = "Switching camera..."
                        stopContinuousFeedbackMonitoring()
                    }

                    Log.d("EnrollFace", "Cancelling biometric client...")
                    biometricClient?.cancel()

                    // Wait for camera to stop capturing (with timeout)
                    var timeoutCounter = 0
                    while (currentCamera.isCapturing && timeoutCounter < 5000) {
                        Thread.sleep(50)
                        timeoutCounter += 50
                        if (timeoutCounter % 500 == 0) {
                            Log.d("EnrollFace", "Waiting for camera to stop... ${timeoutCounter}ms")
                        }
                    }

                    if (currentCamera.isCapturing) {
                        Log.e("EnrollFace", "Camera switch timeout - camera still capturing after 5 seconds")
                        main.post {
                            status = "Camera switch timeout"
                            onComplete?.invoke(false)
                        }
                        return@execute
                    }

                    Log.d("EnrollFace", "Camera stopped successfully")
                }

                // Switch to the new camera
                Log.d("EnrollFace", "Setting new camera as face capture device...")
                biometricClient?.faceCaptureDevice = nextCamera

                // Verify the switch
                val verifyCamera = biometricClient?.faceCaptureDevice
                Log.d("EnrollFace", "Verification - Current camera after switch: ${verifyCamera?.displayName}")

                val cameraName = nextCamera.displayName
                Log.d("EnrollFace", "Successfully switched to camera: $cameraName")

                main.post {
                    status = "Switched to $cameraName"

                    // Restart capturing if it was active
                    if (wasCapturing) {
                        Log.d("EnrollFace", "Restarting capture after switch...")
                        main.postDelayed({
                            startAutomaticCapture()
                        }, 500)
                    }

                    onComplete?.invoke(true)
                }

            } catch (e: Exception) {
                Log.e("EnrollFace", "Error switching camera: ${e.message}", e)
                e.printStackTrace()
                main.post {
                    status = "Camera switch error: ${e.message}"
                    onComplete?.invoke(false)
                }
            }
        }
    }


    fun stopCapturing() {
        executor.execute {
            try {
                stopContinuousFeedbackMonitoring()
                biometricClient?.cancel()
                main.post {
                    isCapturing = false
                    status = "Capture stopped"
                }
            } catch (e: Exception) {
                Log.e("EnrollFace", "Error stopping capture", e)
            }
        }
    }

    fun startAutomaticCapture() {
        executor.execute {
            try {
                main.post { isCapturing = true }
                Log.d("EnrollFace", "Starting automatic capture...")
                main.post {
                    status = "Detecting face..."
                    detectionFeedback = FaceDetectionFeedback(
                        overallMessage = "Looking for face..."
                    )
                }

                val subject = NSubject()
                val face = NFace().apply {
                    captureOptions = EnumSet.of(NBiometricCaptureOption.STREAM)
                }

                subject.faces.add(face)
                main.post { currentSubject = subject }

                // Start continuous feedback monitoring
                startContinuousFeedbackMonitoring(subject)

                val task = biometricClient?.createTask(
                    EnumSet.of(NBiometricOperation.CAPTURE, NBiometricOperation.CREATE_TEMPLATE),
                    subject
                )

                task?.let { biometricClient?.performTask(it) }

                // Stop feedback monitoring
                stopContinuousFeedbackMonitoring()

                val taskStatus = task?.status

                if (taskStatus == NBiometricStatus.OK) {
                    Log.d("EnrollFace", "Face captured successfully!")
                    main.post {
                        status = "Face captured! Processing..."
                        detectionFeedback = FaceDetectionFeedback(
                            lightingStatus = LightingStatus.GOOD,
                            distanceStatus = DistanceStatus.GOOD,
                            positionStatus = PositionStatus.CENTERED,
                            qualityStatus = QualityStatus.EXCELLENT,
                            overallMessage = "Perfect! Hold still! Processing"
                        )
                        saveAndTriggerMatching(subject)
                    }
                } else {
                    val feedback = analyzeFaceAttributes(subject)
                    Log.w("EnrollFace", "Capture failed: $taskStatus")
                    main.post {
                        status = feedback.overallMessage
                        detectionFeedback = feedback
                        main.postDelayed({ startAutomaticCapture() }, 500)
                    }
                }

                task?.dispose()

            } catch (e: Exception) {
                Log.e("EnrollFace", "Error during capture", e)
                stopContinuousFeedbackMonitoring()
                main.post {
                    isCapturing = false
                    status = "Capture error. Retrying..."
                    detectionFeedback = FaceDetectionFeedback(
                        overallMessage = "Capture error. Retrying..."
                    )
                    main.postDelayed({ startAutomaticCapture() }, 800)
                }
            }
        }
    }

    private fun startContinuousFeedbackMonitoring(subject: NSubject) {
        feedbackMonitorJob?.cancel()
        feedbackMonitorJob = feedbackScope.launch {
            while (isActive) {
                try {
                    val feedback = analyzeFaceAttributes(subject)
                    withContext(Dispatchers.Main) {
                        if (!status.contains("captured", ignoreCase = true) &&
                            !status.contains("processing", ignoreCase = true)) {
                            detectionFeedback = feedback
                        }
                    }
                    delay(100) // Update every 100ms
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun stopContinuousFeedbackMonitoring() {
        feedbackMonitorJob?.cancel()
        feedbackMonitorJob = null
    }

    private fun analyzeFaceAttributes(subject: NSubject): FaceDetectionFeedback {
        try {
            val face = subject.faces.firstOrNull()
            val attributes = face?.objects?.firstOrNull() as? NFAttributes

            if (attributes == null) {
                return FaceDetectionFeedback(
                    overallMessage = "No face detected - Please face the camera"
                )
            }

            val sharpness = attributes.quality
            val lighting = when {
                sharpness > 70 -> LightingStatus.GOOD
                sharpness > 50 -> LightingStatus.TOO_DARK
                else -> LightingStatus.TOO_DARK
            }

            val boundingRect = attributes.boundingRect
            val faceWidth = boundingRect.width()
            val distanceStatus = when {
                faceWidth > 400 -> DistanceStatus.TOO_CLOSE
                faceWidth in 200..400 -> DistanceStatus.GOOD
                faceWidth in 100..200 -> DistanceStatus.TOO_FAR
                else -> DistanceStatus.TOO_FAR
            }

            val centerX = boundingRect.centerX() + boundingRect.width() / 2
            val centerY = boundingRect.centerY() + boundingRect.height() / 2
            val positionStatus = when {
                centerX < 250 -> PositionStatus.MOVE_RIGHT
                centerX > 390 -> PositionStatus.MOVE_LEFT
                centerY < 200 -> PositionStatus.MOVE_DOWN
                centerY > 280 -> PositionStatus.MOVE_UP
                else -> PositionStatus.CENTERED
            }

            val quality = attributes.quality
            val qualityStatus = when {
                quality > 80 -> QualityStatus.EXCELLENT
                quality > 60 -> QualityStatus.GOOD
                quality > 40 -> QualityStatus.FAIR
                else -> QualityStatus.POOR
            }

            val messages = mutableListOf<String>()

            when (lighting) {
                LightingStatus.GOOD -> messages.add("✓ Lighting OK")
                LightingStatus.TOO_DARK -> messages.add("⚠ Too dark")
                LightingStatus.TOO_BRIGHT -> messages.add("⚠ Too bright")
                else -> {}
            }

            when (distanceStatus) {
                DistanceStatus.GOOD -> messages.add("✓ Distance OK")
                DistanceStatus.TOO_FAR -> messages.add("⚠ Move closer")
                DistanceStatus.TOO_CLOSE -> messages.add("⚠ Move back")
                else -> {}
            }

            when (positionStatus) {
                PositionStatus.CENTERED -> messages.add("✓ Position OK")
                PositionStatus.MOVE_LEFT -> messages.add("← Move left")
                PositionStatus.MOVE_RIGHT -> messages.add("→ Move right")
                PositionStatus.MOVE_UP -> messages.add("↑ Move up")
                PositionStatus.MOVE_DOWN -> messages.add("↓ Move down")
                else -> {}
            }

            val overallMessage = if (messages.isEmpty()) {
                "Adjusting... Hold still"
            } else {
                messages.joinToString(" • ")
            }

            return FaceDetectionFeedback(
                lightingStatus = lighting,
                distanceStatus = distanceStatus,
                positionStatus = positionStatus,
                qualityStatus = qualityStatus,
                overallMessage = overallMessage
            )

        } catch (e: Exception) {
            Log.e("EnrollFace", "Error analyzing face attributes", e)
            return FaceDetectionFeedback(
                overallMessage = "Analyzing... Please hold still"
            )
        }
    }

    private fun saveAndTriggerMatching(subject: NSubject) {
        try {
            val face = subject.faces[0]
            val imageBytes = face.image?.save()?.toByteArray()
            val templateBytes = subject.templateBuffer?.toByteArray()

            if (imageBytes != null && templateBytes != null) {
                capturedFaceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                main.post{
                    onFaceDetectedSound?.invoke()
                }
                executor.execute {
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
                        onFaceCaptured?.invoke(imageBytes, templateBytes)

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

    fun matchWithNFCFace(nfcFaceBytes: ByteArray) {
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

                nfcFaceBitmap = BitmapFactory.decodeByteArray(nfcFaceBytes, 0, nfcFaceBytes.size)

                val capturedSubject = NSubject()
                capturedSubject.setTemplateBuffer(NBuffer(latestCapture.template))

                val nfcSubject = NSubject()
                val nfcFace = NFace()
                val nfcImage = NImage.fromMemory(NBuffer(nfcFaceBytes))
                nfcFace.image = nfcImage
                nfcSubject.faces.add(nfcFace)

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
        detectionFeedback = FaceDetectionFeedback()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            stopContinuousFeedbackMonitoring()
            feedbackScope.cancel()
            biometricClient?.cancel()
            biometricClient?.dispose()
            currentSubject = null
        } catch (e: Exception) {
            // Ignore
        }
        executor.shutdown()
    }
}