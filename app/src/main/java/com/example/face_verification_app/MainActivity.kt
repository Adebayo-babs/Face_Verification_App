package com.example.face_verification_app

import android.Manifest.permission.CAMERA
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.common.apiutil.nfc.Nfc
import com.example.common.FaceMatchResult
import com.example.face_verification_app.data.CardVerificationResponse
import com.example.face_verification_app.ui.CardDetailsScreen
import com.example.face_verification_app.ui.CardVerificationDialog
import com.example.face_verification_app.ui.FaceVerificationResultScreen
import com.example.face_verification_app.ui.MainMenuScreen
import com.example.face_verification_app.ui.SplashScreen
import com.example.face_verification_app.ui.theme.Face_Verification_AppTheme
import com.example.neurotecsdklibrary.EnrollFaceScreen
import com.example.neurotecsdklibrary.EnrollFaceViewModel
import com.example.neurotecsdklibrary.NeurotecFaceManager
import com.example.neurotecsdklibrary.NeurotecLicenseHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var sharedPreferences: SharedPreferences

    // Navigation States
    private enum class Screen {
        SPLASH,
        MAIN_MENU,
        CARD_DETAILS,
        CAMERA_CAPTURE,
        FACE_VERIFICATION
    }

    // NFC Setup
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    // Card reader instances
    private val samCardReader = SAMCardReader()

    private var cardReadingEnabled = true

    // Face & Card state
    private var pendingCardData by mutableStateOf<SAMCardReader.SecureCardData?>(null)
    private var capturedFaceBitmap by mutableStateOf<Bitmap?>(null)
    private var nfcFaceBitmap by mutableStateOf<Bitmap?>(null)
    private var faceMatchResult by mutableStateOf<FaceMatchResult?>(null)
    private var lastScannedCardId by mutableStateOf<String?>(null)
    private var showFaceVerificationDialog by mutableStateOf(false)

    // Store NFC face for matching
    private var nfcFaceImage by mutableStateOf<ByteArray?>(null)

    // Dialog state
    private var showVerificationDialog by mutableStateOf(false)
    private var verificationResponse by mutableStateOf<CardVerificationResponse?>(null)
    private var verificationCardId by mutableStateOf("")

    // Compose navigation
    private var currentScreen by mutableStateOf(Screen.SPLASH)
    private var isMainMenuActive by mutableStateOf(false)
    private var isFaceVerificationActive by mutableStateOf(false)

    private var readingJob: Job? = null

    // Neurotec Face Manager
    private var neurotecFaceManager: NeurotecFaceManager? = null
    private var isNeurotecAvailable = false

    // Telpo T20 NFC
    private lateinit var telpoT20DataSource: TelpoT20DataSourceImpl


    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            currentScreen = Screen.CAMERA_CAPTURE
        } else {
            Toast.makeText(this,
                "Camera permission required for face verification",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("CardAppPrefs", Context.MODE_PRIVATE)
//        mediaPlayer = MediaPlayer.create(this, R.raw.success)

        Log.d(TAG, "MainActivity onCreate - checking licenses...")
        Log.d(TAG, "Licenses activated in Application: ${FaceMatchApplication.areLicensesActivated}")

        // Check if licenses are activated and create the manager
        if (FaceMatchApplication.areLicensesActivated) {
            try {
                Log.d(TAG, "Creating NeurotecFaceManager...")
                neurotecFaceManager = NeurotecFaceManager(this)
                isNeurotecAvailable = true
                Log.d(TAG, " Face manager initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, " Failed to create face manager", e)
                isNeurotecAvailable = false
            }
        } else {
            Log.e(TAG, " Licenses not activated")
            isNeurotecAvailable = false
        }

        // Setup NFC for standard Android NFC
        setupNFC()
        telpoT20DataSource = TelpoT20DataSourceImpl(this)
        startTelpoCardReading() {
            lifecycleScope.launch {
                telpoT20DataSource.tagFlow.collect { nfc ->
                    Log.d(TAG, "Telpo card tapped: $nfc")
                    handleTelpoCard(nfc)
                }
            }
            telpoT20DataSource.start()
        }

        enableEdgeToEdge()
        setContent {
            Face_Verification_AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        Screen.SPLASH -> {
                            SplashScreen (
                                onNavigateToMain = {
                                    currentScreen = Screen.MAIN_MENU
                                    isMainMenuActive = true
                                }
                            )
                        }

                        Screen.MAIN_MENU -> {
                            MainMenuScreen(
                                isLoading = showFaceVerificationDialog,
                                onChangeSAMPassword = { newPassword ->
                                    saveSAMPassword(newPassword)
                                }
                            )
                        }

                        Screen.CARD_DETAILS -> {
                            CardDetailsScreen(
                                cardData = pendingCardData,
                                onBackClick = {
                                    currentScreen = Screen.MAIN_MENU
                                    isMainMenuActive = true
                                    cardReadingEnabled = true
                                    telpoT20DataSource.resume()

                                },
                                onVerifyFaceClick = {
                                    // Proceed to face verification
                                    if (checkCameraPermission()) {
                                        currentScreen = Screen.CAMERA_CAPTURE
                                        isFaceVerificationActive = true
                                    }
                                }
                            )
                        }

                        Screen.CAMERA_CAPTURE -> {
                            AutoCaptureAndMatchScreen(nfcFaceImage = nfcFaceImage,
                                onCapturedFaceReady = { bitmap ->
                                    capturedFaceBitmap = bitmap
                                },
                                onNfcFaceReady = { bitmap ->
                                    nfcFaceBitmap = bitmap
                                },
                                onMatchResult = { result ->
                                    faceMatchResult = result
                                },
                                onNavigateToResult = {
                                    currentScreen = Screen.FACE_VERIFICATION
                                }

                            )
                        }

                        Screen.FACE_VERIFICATION -> {
                            FaceVerificationResultScreen(
                                capturedFace = capturedFaceBitmap,
                                nfcFace = nfcFaceBitmap,
                                matchResult = faceMatchResult,
                                cardData = pendingCardData,
                                onRetakePhoto = {
                                    if (checkCameraPermission()) {
                                        currentScreen = Screen.CAMERA_CAPTURE
                                    }
                                },
                                onConfirm = {
                                    if (faceMatchResult?.isMatch == true) {
                                        completeFaceVerification()
                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Face verification failed. Cannot proceed.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                onCancel = {
                                    currentScreen = Screen.MAIN_MENU
                                    isMainMenuActive = true

                                    cardReadingEnabled = true
                                    telpoT20DataSource.resume()

                                }
                            )
                        }



//                        Screen.SAM_PASSWORD_INPUT -> {
//                            Toast.makeText(this, "SAM Password Input screen not yet implemented", Toast.LENGTH_SHORT).show()
//                        }


                    }


                    // Show card verification dialog when needed
                    if (showVerificationDialog && verificationResponse != null) {
                        CardVerificationDialog(
                            cardId = verificationCardId,
                            success = verificationResponse!!.success,
                            message = verificationResponse!!.message,
                            additionalData = verificationResponse!!.data,
                            onDismiss = {
                                showVerificationDialog = false
                                verificationResponse = null
                                verificationCardId = ""
                            }
                        )
                    }
                }
            }
        }
    }

    // Update the SAM_PASSWORD constant to be a variable
    private fun getSAMPassword(): String {
        return sharedPreferences.getString("SAM_PASSWORD", "2EC93602960F9B09D858BB00B2C8E486")
            ?: "2EC93602960F9B09D858BB00B2C8E486"
    }

    private fun saveSAMPassword(password: String) {
        sharedPreferences.edit().putString("SAM_PASSWORD", password).apply()
        android.widget.Toast.makeText(
            this,
            "SAM Password updated successfully",
            Toast.LENGTH_SHORT
        ).show()
        Log.d(TAG, "SAM Password saved: ${password.take(8)}...") // Only log first 8 chars for security
    }

    private fun setupNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tag = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFiltersArray = arrayOf(ndef, tech, tag)

        techListsArray = arrayOf(
            arrayOf(IsoDep::class.java.name)
        )
    }

    private fun checkCameraPermission(): Boolean {
        if (!isNeurotecAvailable) {
            Toast.makeText(
                this,
                "Face verification unavailable: Neurotec SDK not configured",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        return when {
            ContextCompat.checkSelfPermission(
                this,
                CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                true
            }
            else -> {
                cameraPermissionLauncher.launch(CAMERA)
                false
            }
        }
    }

    private fun startTelpoCardReading(function: () -> Unit) {
        lifecycleScope.launch {
            telpoT20DataSource.tagFlow.collect { nfc ->
                if (!cardReadingEnabled) {
                    Log.d(TAG, "Card reading locked; ignoring tap")
                    return@collect
                }

                cardReadingEnabled = false
                Log.d(TAG, "Card tapping accepted; processing begins")
                handleTelpoCard(nfc)
            }
        }
        telpoT20DataSource.start()
    }

    private fun handleTelpoCard(nfc: Nfc) {
        // Prevent concurrent reads
        if (readingJob?.isActive == true) {
            Log.d(TAG, "Already processing a card, ignoring")
            return
        }

        showFaceVerificationDialog = true
        mediaPlayer?.start()

        readingJob = lifecycleScope.launch {
            try {

                Log.d(TAG, "Starting SAM card read...")

                val secureCardData = withContext(Dispatchers.IO) {
                    samCardReader.readSecureCardData(
                        nfcDevice = nfc,
                        samPassword = getSAMPassword(),
                        samKeyIndex = 0x01
                    )
                }

                showFaceVerificationDialog = false
                Log.d(TAG, "SAM card read complete")

                if (!secureCardData.isAuthenticated) {
                    Toast.makeText(
                        this@MainActivity,
                        " Authentication failed! Check SAM Password",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }


                pendingCardData = secureCardData
                lastScannedCardId = secureCardData.cardId
                nfcFaceImage = secureCardData.faceImage

                isMainMenuActive = false
                currentScreen = Screen.CARD_DETAILS


            } catch (e: Exception) {
                showFaceVerificationDialog = false
                Log.e(TAG, "Error reading Telpo card", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error reading card: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

            } finally {
                readingJob = null
            }
        }
    }

    private fun completeFaceVerification() {
        lifecycleScope.launch {
            val cardData = pendingCardData

            if (cardData == null || cardData.cardId == null) {
                Toast.makeText(
                    this@MainActivity,
                    "No card data available",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            try {
                // Call API to verify card with face match
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Verifying card with face match...",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error completing verification", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this,
            pendingIntent,
            intentFiltersArray,
            techListsArray
        )
        if (isMainMenuActive) {
            telpoT20DataSource.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel any active reading operation
        readingJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaPlayer?.release()
        mediaPlayer = null


        // Release Neurotec licenses
        try {
            if (isNeurotecAvailable) {
                NeurotecLicenseHelper.release()
            }

            // Release face manager
            neurotecFaceManager?.release()
            neurotecFaceManager = null

            Log.d(TAG, "MainActivity destroyed, resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }

    }

}

