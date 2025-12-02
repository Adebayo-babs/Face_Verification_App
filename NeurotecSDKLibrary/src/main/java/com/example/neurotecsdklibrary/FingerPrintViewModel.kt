package com.example.neurotecsdklibrary

import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.neurotec.biometrics.NBiometricOperation
import com.neurotec.biometrics.NBiometricStatus
import com.neurotec.biometrics.NFinger
import com.neurotec.biometrics.NSubject
import com.neurotec.biometrics.client.NBiometricClient
import com.neurotec.io.NBuffer
import java.util.EnumSet
import java.util.concurrent.Executors

data class FingerprintMatchResult(
    val capturedFingerprint: ByteArray?,
    val nfcFingerprint: ByteArray?,
    val score: Int,
    val threshold: Int = 48,
    val isMatch: Boolean = score >= threshold,
    val matchPercentage: Float = (score / 100f) * 100f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FingerprintMatchResult
        if (capturedFingerprint != null) {
            if (other.capturedFingerprint == null) return false
            if (!capturedFingerprint.contentEquals(other.capturedFingerprint)) return false
        } else if (other.capturedFingerprint != null) return false
        if (nfcFingerprint != null) {
            if (other.nfcFingerprint == null) return false
            if (!nfcFingerprint.contentEquals(other.nfcFingerprint)) return false
        } else if (other.nfcFingerprint != null) return false
        if (score != other.score) return false
        if (threshold != other.threshold) return false
        return true
    }

    override fun hashCode(): Int {
        var result = capturedFingerprint?.contentHashCode() ?: 0
        result = 31 * result + (nfcFingerprint?.contentHashCode() ?: 0)
        result = 31 * result + score
        result = 31 * result + threshold
        return result
    }
}

enum class FingerprintCaptureStatus {
    IDLE,
    WAITING_FOR_FINGER,
    FINGER_DETECTED,
    CAPTURING,
    CAPTURED,
    PROCESSING,
    MATCHING,
    COMPLETED,
    ERROR
}

class FingerprintVerificationViewModel(application: Application) : AndroidViewModel(application) {

    var status by mutableStateOf("")
        private set

    var captureStatus by mutableStateOf(FingerprintCaptureStatus.IDLE)
        private set

    var matchResult by mutableStateOf<FingerprintMatchResult?>(null)
        private set

    var biometricClient: NBiometricClient? = null
        private set

    var capturedFingerprintTemplate by mutableStateOf<ByteArray?>(null)
        private set

    var nfcFingerprintTemplate by mutableStateOf<ByteArray?>(null)
        private set

    var onFingerprintCaptured: ((ByteArray) -> Unit)? = null
    var onMatchComplete: ((FingerprintMatchResult) -> Unit)? = null
    var onCaptureSound: (() -> Unit)? = null

    private var nfcCardFingerprintData: ByteArray? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "FingerprintVM"
    }

    fun initialize() {
        executor.execute {
            try {
                NeurotecLicenseHelper.obtain(getApplication())
                main.post {
                    status = "Fingerprint SDK initialized"
                    initClient()
                }
            } catch (e: Exception) {
                main.post {
                    status = "License Error: ${e.message}"
                    captureStatus = FingerprintCaptureStatus.ERROR
                }
            }
        }
    }

    private fun initClient() {
        try {
            biometricClient = NBiometricClient().apply {
                fingersQualityThreshold = 40
//                fingersMatchingThreshold = 48
                initialize()
            }

            main.post {
                status = "Ready for fingerprint capture"
                captureStatus = FingerprintCaptureStatus.IDLE
            }
        } catch (e: Exception) {
            main.post {
                status = "Initialization error: ${e.message}"
                captureStatus = FingerprintCaptureStatus.ERROR
            }
        }
    }


    fun setNFCFingerprintData(fingerprintTemplate: ByteArray) {
        nfcCardFingerprintData = fingerprintTemplate
        nfcFingerprintTemplate = fingerprintTemplate
        Log.d(TAG, "NFC fingerprint data set: ${fingerprintTemplate.size} bytes")
    }


    fun startFingerprintCapture() {
        executor.execute {
            try {
                Log.d(TAG, "Starting fingerprint capture...")
                main.post {
                    status = "Place your finger on the scanner..."
                    captureStatus = FingerprintCaptureStatus.WAITING_FOR_FINGER
                }

                val subject = NSubject()
                val finger = NFinger()
                subject.fingers.add(finger)

                val task = biometricClient?.createTask(
                    EnumSet.of(NBiometricOperation.CAPTURE, NBiometricOperation.CREATE_TEMPLATE),
                    subject
                )

                main.post {
                    captureStatus = FingerprintCaptureStatus.CAPTURING
                    status = "Capturing fingerprint..."
                }

                task?.let { biometricClient?.performTask(it) }

                val taskStatus = task?.status

                if (taskStatus == NBiometricStatus.OK) {
                    Log.d(TAG, "Fingerprint captured successfully!")

                    val templateBytes = subject.templateBuffer?.toByteArray()

                    if (templateBytes != null) {
                        capturedFingerprintTemplate = templateBytes

                        main.post {
                            status = "Fingerprint captured successfully!"
                            captureStatus = FingerprintCaptureStatus.CAPTURED
                            onCaptureSound?.invoke()
                            onFingerprintCaptured?.invoke(templateBytes)

                            // Auto-match if NFC data is available
                            nfcCardFingerprintData?.let { nfcTemplate ->
                                matchWithNFCFingerprint(nfcTemplate)
                            }
                        }
                    } else {
                        main.post {
                            status = "Failed to extract fingerprint template"
                            captureStatus = FingerprintCaptureStatus.ERROR
                        }
                    }
                } else {
                    Log.w(TAG, "Capture failed: $taskStatus")
                    main.post {
                        status = "Capture failed. Please try again."
                        captureStatus = FingerprintCaptureStatus.ERROR
                    }
                }

                task?.dispose()

            } catch (e: Exception) {
                Log.e(TAG, "Error during capture", e)
                main.post {
                    status = "Capture error: ${e.message}"
                    captureStatus = FingerprintCaptureStatus.ERROR
                }
            }
        }
    }

    fun matchWithNFCFingerprint(nfcTemplateBytes: ByteArray) {
        executor.execute {
            try {
                main.post {
                    status = "Matching fingerprints..."
                    captureStatus = FingerprintCaptureStatus.MATCHING
                }

                val capturedTemplate = capturedFingerprintTemplate

                if (capturedTemplate == null) {
                    main.post {
                        status = "No captured fingerprint found"
                        captureStatus = FingerprintCaptureStatus.ERROR
                    }
                    return@execute
                }

                // Create subject from captured template
                val capturedSubject = NSubject()
                capturedSubject.setTemplateBuffer(NBuffer(capturedTemplate))

                // Create subject from NFC template
                val nfcSubject = NSubject()
                nfcSubject.setTemplateBuffer(NBuffer(nfcTemplateBytes))

                main.post { status = "Comparing fingerprints..." }

                val matchStatus = biometricClient?.verify(capturedSubject, nfcSubject)

                val score = if (matchStatus == NBiometricStatus.OK) {
                    capturedSubject.matchingResults?.getOrNull(0)?.score ?: 0
                } else {
                    0
                }

                val result = FingerprintMatchResult(
                    capturedFingerprint = capturedTemplate,
                    nfcFingerprint = nfcTemplateBytes,
                    score = score,
                    threshold = 48
                )

                main.post {
                    matchResult = result
                    status = if (result.isMatch) {
                        "Fingerprints Match! (Score: $score)"
                    } else {
                        "Fingerprints Do Not Match (Score: $score)"
                    }
                    captureStatus = FingerprintCaptureStatus.COMPLETED
                    onMatchComplete?.invoke(result)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during matching", e)
                main.post {
                    status = "Matching error: ${e.message}"
                    captureStatus = FingerprintCaptureStatus.ERROR
                }
            }
        }
    }

    fun matchFingerprints(template1: ByteArray, template2: ByteArray, callback: (FingerprintMatchResult) -> Unit) {
        executor.execute {
            try {
                val subject1 = NSubject()
                subject1.setTemplateBuffer(NBuffer(template1))

                val subject2 = NSubject()
                subject2.setTemplateBuffer(NBuffer(template2))

                val matchStatus = biometricClient?.verify(subject1, subject2)

                val score = if (matchStatus == NBiometricStatus.OK) {
                    subject1.matchingResults?.getOrNull(0)?.score ?: 0
                } else {
                    0
                }

                val result = FingerprintMatchResult(
                    capturedFingerprint = template1,
                    nfcFingerprint = template2,
                    score = score
                )

                main.post { callback(result) }

            } catch (e: Exception) {
                Log.e(TAG, "Error matching fingerprints", e)
                main.post {
                    callback(FingerprintMatchResult(
                        capturedFingerprint = template1,
                        nfcFingerprint = template2,
                        score = 0
                    ))
                }
            }
        }
    }

    fun reset() {
        matchResult = null
        capturedFingerprintTemplate = null
        nfcFingerprintTemplate = null
        nfcCardFingerprintData = null
        status = ""
        captureStatus = FingerprintCaptureStatus.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        try {
            biometricClient?.cancel()
            biometricClient?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing biometric client", e)
        }
        executor.shutdown()
    }
}