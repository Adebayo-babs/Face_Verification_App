package com.example.face_verification_app

import android.Manifest.permission.CAMERA
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.common.CommonConstants
import com.common.apiutil.nfc.Nfc
import com.example.common.FaceMatchResult
import com.example.face_verification_app.data.CardVerificationResponse
import com.example.face_verification_app.data.OptimizedCardDataReader
import com.example.face_verification_app.ui.AccessGrantedDialog
import com.example.face_verification_app.ui.CardDetailsScreen
import com.example.face_verification_app.ui.CardReadDialog
import com.example.face_verification_app.ui.CardVerificationDialog
import com.example.face_verification_app.ui.FaceVerificationResultScreen
import com.example.face_verification_app.ui.MainMenuScreen
import com.example.face_verification_app.ui.SplashScreen
import com.example.face_verification_app.ui.theme.Face_Verification_AppTheme
import com.example.neurotecsdklibrary.NeurotecFaceManager
import com.example.neurotecsdklibrary.NeurotecLicenseHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var cardTapPlayer: MediaPlayer? = null
    private var faceDetectedPlayer: MediaPlayer? = null

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

    // Face Verification enabled
    private var faceVerificationEnabled by mutableStateOf(true)
    private var showAccessGrantedDialog by mutableStateOf(false)

    // NFC Setup
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    // Card reading dialog
    private var showCardReadDialog by mutableStateOf(false)
    private var cardReadSuccess by mutableStateOf(false)
    private var cardReadMessage by mutableStateOf("")

    // Card reader instances
    private val samCardReader = SAMCardReader()

    private var cardReadingEnabled = true

    // Face & Card state
    private var pendingCardData by mutableStateOf<SAMCardReader.SecureCardData?>(null)
    private var accessCardData by mutableStateOf<OptimizedCardDataReader.CardData?>(null)
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
            // Go back to main menu if permission denied
            currentScreen = Screen.MAIN_MENU
            isMainMenuActive = true
            cardReadingEnabled = true
            telpoT20DataSource.resume()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("CardAppPrefs", Context.MODE_PRIVATE)

        // Load face verification setting
        faceVerificationEnabled = loadFaceVerificationSetting()
        cardTapPlayer = MediaPlayer.create(this, R.raw.success)
        faceDetectedPlayer = MediaPlayer.create(this, R.raw.success)

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
                                },
                                faceVerificationEnabled = faceVerificationEnabled,
                                onToggleFaceVerification = { enabled ->
                                    saveFaceVerificationEnabled(enabled)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Face verification ${if (enabled) "enabled" else "disabled"}",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                }
                            )
                        }

                        Screen.CARD_DETAILS -> {
                            CardDetailsScreen(
                                cardData = pendingCardData,
                                onBackClick = {
                                    resetCardState()
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
                                },
                                onBackToMainMenu = {
                                    resetCardState()
//                                    resetVerificationState()
                                    currentScreen = Screen.MAIN_MENU
                                    isMainMenuActive = true
                                    cardReadingEnabled = true
                                    telpoT20DataSource.resume()
                                },
                                onPlayFaceDetectedSound = {
//                                    faceDetectedPlayer?.start()
                                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 1000)
                                    toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
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
                                    resetVerificationResults()
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
                                    // Reset state when going back to main menu
                                    resetCardState()
//                                    resetVerificationState()
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

                    // Show card read dialog
                    if (showCardReadDialog) {
                        CardReadDialog(
                            success = cardReadSuccess,
                            message = cardReadMessage,
                            onDismiss = {
                                showCardReadDialog = false
                                if (cardReadSuccess) {
                                    // Proceed to face verification
                                    if (checkCameraPermission()) {
                                        currentScreen = Screen.CAMERA_CAPTURE
                                        isFaceVerificationActive = true
                                    } else {
                                        resetCardState()
                                        currentScreen = Screen.MAIN_MENU
                                        isMainMenuActive = true
                                        cardReadingEnabled = true
                                        telpoT20DataSource.resume()
                                    }
                                } else {
                                    // Stay on main menu for failures
                                    resetCardState()
                                    currentScreen = Screen.MAIN_MENU
                                    isMainMenuActive = true
                                    cardReadingEnabled = true
                                    telpoT20DataSource.resume()
                                }
                            }
                        )
                    }

                    if (showAccessGrantedDialog) {
                        AccessGrantedDialog(
                            cardData = accessCardData,
                            onDismiss = {
                                showAccessGrantedDialog = false
                                resetCardState()
                                accessCardData = null
                                currentScreen = Screen.MAIN_MENU
                                isMainMenuActive = true
                                cardReadingEnabled = true
                                telpoT20DataSource.resume()
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
        Toast.makeText(
            this,
            "SAM Password updated successfully",
            Toast.LENGTH_SHORT
        ).show()
        Log.d(TAG, "SAM Password saved: ${password.take(8)}...") // Only log first 8 chars for security
    }

    // Update getSAMPassword() section to include the new preference
    private fun loadFaceVerificationSetting(): Boolean {
        return sharedPreferences.getBoolean("FACE_VERIFICATION_ENABLED", true)
    }

    private fun saveFaceVerificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("FACE_VERIFICATION_ENABLED", enabled).apply()
        faceVerificationEnabled = enabled
        Log.d(TAG, "Face verification enabled: $enabled")
    }

    // Handle Relay
    private fun activateRelayAndShowSuccess() {
        lifecycleScope.launch {
            try {
                // Turn on relay
                val commonUtil = com.common.apiutil.pos.CommonUtil(this@MainActivity)
                commonUtil.setRelayPower(CommonConstants.RelayType.RELAY_1, 1)
                Log.d(TAG, "Relay turned ON")

                // Show success message
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Access Granted - Card Verified",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Wait 2 seconds
                delay(2000)

                // Turn off relay
                commonUtil.setRelayPower(CommonConstants.RelayType.RELAY_1, 0)
                Log.d(TAG, "Relay turned OFF")

                // Reset state and go back to main menu
                withContext(Dispatchers.Main) {
                    resetCardState()
                    currentScreen = Screen.MAIN_MENU
                    isMainMenuActive = true
                    cardReadingEnabled = true
                    telpoT20DataSource.resume()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error controlling relay", e)
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
            cardReadingEnabled = true  // Re-enable if already processing
            return
        }

        resetCardState()
        showFaceVerificationDialog = true
        cardTapPlayer?.start()

        readingJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting SAM card read...")

                val secureCardData = withContext(Dispatchers.IO) {
                    samCardReader.readSecureCardData(
                        nfcDevice = nfc,
                        samPassword = getSAMPassword(),
                        samKeyIndex = 0x01,
                        fastMode = !faceVerificationEnabled
                    )
                }

                showFaceVerificationDialog = false
                Log.d(TAG, "SAM card read complete")

                if (!secureCardData.isAuthenticated) {
                    resetCardState()
                    cardReadSuccess = false
                    cardReadMessage = "Authentication failed! Check SAM Password"
                    showCardReadDialog = true
                    cardReadingEnabled = true
                    return@launch
                }

                // Store basic card data
                pendingCardData = secureCardData
                lastScannedCardId = secureCardData.cardId
                isMainMenuActive = false

                if (!faceVerificationEnabled) {
                    // Face verification is OFF - just check if we have basic card data
                    Log.d(TAG, "Face verification disabled, showing access granted dialog")

                    // Convert to OptimizedCardDataReader.CardData format for the dialog
                    accessCardData = OptimizedCardDataReader.CardData(
                        holderName = secureCardData.firstName,
                        cardId = secureCardData.cardId,
                        expirationDate = null,
                        applicationLabel = null,
                        pan = null
                    )

                    // Show the access granted dialog
                    showAccessGrantedDialog = true

                    // Activate relay in background
                    lifecycleScope.launch {
                        try {
                            val commonUtil = com.common.apiutil.pos.CommonUtil(this@MainActivity)
                            commonUtil.setRelayPower(CommonConstants.RelayType.RELAY_1, 1)
                            Log.d(TAG, "Relay turned ON")

                            // Wait 2 seconds
                            delay(5000)

                            // Turn off relay
                            commonUtil.setRelayPower(CommonConstants.RelayType.RELAY_1, 0)
                            Log.d(TAG, "Relay turned OFF")

                            // Auto-dismiss dialog after 2 seconds
                            withContext(Dispatchers.Main) {
                                showAccessGrantedDialog = false
                                resetCardState()
                                currentScreen = Screen.MAIN_MENU
                                isMainMenuActive = true
                                cardReadingEnabled = true
                                telpoT20DataSource.resume()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error controlling relay", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Still reset even on error
                                showAccessGrantedDialog = false
                                resetCardState()
                                currentScreen = Screen.MAIN_MENU
                                isMainMenuActive = true
                                cardReadingEnabled = true
                                telpoT20DataSource.resume()
                            }
                        }
                    }
                } else {
                    // Face verification is ON - check if face image was read successfully
                    if (secureCardData.faceImage == null || secureCardData.faceImage.isEmpty()) {
                        resetCardState()
                        cardReadSuccess = false
                        cardReadMessage = "Card not read, please keep the card on reader"
                        showCardReadDialog = true
                        cardReadingEnabled = true
                        return@launch
                    }

                    nfcFaceImage = secureCardData.faceImage

                    // Show success dialog for face verification
                    cardReadSuccess = true
                    cardReadMessage = "Card read successfully, ready for face verification"
                    showCardReadDialog = true
                }

            } catch (e: Exception) {
                showFaceVerificationDialog = false
                Log.e(TAG, "Error reading Telpo card", e)
                resetCardState()
                cardReadSuccess = false
                cardReadMessage = "Error reading card: ${e.message}"
                showCardReadDialog = true
                cardReadingEnabled = true

            } finally {
                readingJob = null
            }
        }
    }

//    private fun handleTelpoCard(nfc: Nfc) {
//        // Prevent concurrent reads
//        if (readingJob?.isActive == true) {
//            Log.d(TAG, "Already processing a card, ignoring")
//            cardReadingEnabled = true  // Re-enable if already processing
//            return
//        }
//
//        resetCardState()
//        showFaceVerificationDialog = true
//        cardTapPlayer?.start()
//
//        readingJob = lifecycleScope.launch {
//            try {
//                Log.d(TAG, "Starting SAM card read...")
//
//                val secureCardData = withContext(Dispatchers.IO) {
//                    samCardReader.readSecureCardData(
//                        nfcDevice = nfc,
//                        samPassword = getSAMPassword(),
//                        samKeyIndex = 0x01
//                    )
//                }
//
//                showFaceVerificationDialog = false
//                Log.d(TAG, "SAM card read complete")
//
//                if (!secureCardData.isAuthenticated) {
//                    cardReadSuccess = false
//                    cardReadMessage = "Authentication failed! Check SAM Password"
//                    showCardReadDialog = true
//                    cardReadingEnabled = true
//                    return@launch
//                }
//
//                // Check if face image was read successfully
//                if (secureCardData.faceImage == null || secureCardData.faceImage.isEmpty()) {
//                    cardReadSuccess = false
//                    cardReadMessage = "Card not read, please keep the card on reader"
//                    showCardReadDialog = true
//                    cardReadingEnabled = true
//                    return@launch
//                }
//
//                pendingCardData = secureCardData
//                lastScannedCardId = secureCardData.cardId
//                nfcFaceImage = secureCardData.faceImage
//                isMainMenuActive = false
//
//                if (!faceVerificationEnabled) {
//                    // Face verification is OFF - just activate relay and show success
//                    Log.d(TAG, "Face verification disabled, activating relay")
//
//                    // Convert SecureCardData to OptimizedCardDataReader.CardData for the dialog
//                    accessCardData = OptimizedCardDataReader.CardData(
//                        holderName = secureCardData.holderName,
//                        cardId = secureCardData.cardId,
//                        faceImage = secureCardData.faceImage
//                    )
//
//                    showAccessGrantedDialog = true
//                    activateRelayAndShowSuccess()
//                } else {
//                    // Face Verification
//                    cardReadSuccess = true
//                    cardReadMessage = "Card read successfully, ready for face verification"
//                    showCardReadDialog = true
//                }
//
//            } catch (e: Exception) {
//                showFaceVerificationDialog = false
//                Log.e(TAG, "Error reading Telpo card", e)
//                cardReadSuccess = false
//                cardReadMessage = "Error reading card: ${e.message}"
//                showCardReadDialog = true
//                cardReadingEnabled = true
//
//            } finally {
//                readingJob = null
//            }
//        }
//    }


//    private fun handleTelpoCard(nfc: Nfc) {
//        // Prevent concurrent reads
//        if (readingJob?.isActive == true) {
//            Log.d(TAG, "Already processing a card, ignoring")
//            cardReadingEnabled = true  // Re-enable if already processing
//            return
//        }
//
//        resetCardState()
//
//        showFaceVerificationDialog = true
//        cardTapPlayer?.start()
//
//        readingJob = lifecycleScope.launch {
//            try {
//                Log.d(TAG, "Starting SAM card read...")
//
//                val secureCardData = withContext(Dispatchers.IO) {
//                    samCardReader.readSecureCardData(
//                        nfcDevice = nfc,
//                        samPassword = getSAMPassword(),
//                        samKeyIndex = 0x01
//                    )
//                }
//
//                showFaceVerificationDialog = false
//                Log.d(TAG, "SAM card read complete")
//
//                if (!secureCardData.isAuthenticated) {
//                    cardReadSuccess = false
//                    showCardReadDialog = true
//                    Toast.makeText(
//                        this@MainActivity,
//                        "Authentication failed! Check SAM Password",
//                        Toast.LENGTH_LONG
//                    ).show()
//                    cardReadingEnabled = true
//                    return@launch
//                }
//
//                // Check if face image was read successfully
//                if (secureCardData.faceImage == null || secureCardData.faceImage.isEmpty()) {
//                    cardReadSuccess = false
//                    cardReadMessage = "Card not read, please keep the card on reader for 2 seconds"
//                    showCardReadDialog = true
//                    cardReadingEnabled = true
//                    return@launch
//                }
//
////                // Check if face image was read successfully
////                if (secureCardData.faceImage == null || secureCardData.faceImage.isEmpty()) {
////                    Toast.makeText(
////                        this@MainActivity,
////                        "Face not read, please keep the card on reader for 2 seconds",
////                        Toast.LENGTH_LONG
////                    ).show()
////                    cardReadingEnabled = true
////                    return@launch
////                }
//
//                pendingCardData = secureCardData
//                lastScannedCardId = secureCardData.cardId
//                nfcFaceImage = secureCardData.faceImage
//
//                isMainMenuActive = false
//
//                // Show success dialog
//                cardReadSuccess = true
//                cardReadMessage = "Card read successfully, ready for face verification"
//                showCardReadDialog = true
//
//                // Show success message before proceeding
////                Toast.makeText(
////                    this@MainActivity,
////                    "Card Read, ready for face verification",
////                    Toast.LENGTH_SHORT
////                ).show()
//
//                // Small delay to let user see the message
////                delay(800)
//
//                // Skip card details screen and go directly to face capture
////                if (checkCameraPermission()) {
////                    currentScreen = Screen.CAMERA_CAPTURE
////                    isFaceVerificationActive = true
////                } else {
////                    // If permission is denied, stay on the main menu
////                    currentScreen = Screen.MAIN_MENU
////                    isMainMenuActive = true
////                    cardReadingEnabled = true
////                }
//
//            } catch (e: Exception) {
//                showFaceVerificationDialog = false
//                Log.e(TAG, "Error reading Telpo card", e)
//                Toast.makeText(
//                    this@MainActivity,
//                    "Error reading card: ${e.message}",
//                    Toast.LENGTH_LONG
//                ).show()
//                cardReadingEnabled = true
//
//            } finally {
//                readingJob = null
//            }
//        }
//    }


//    private fun handleTelpoCard(nfc: Nfc) {
//        // Prevent concurrent reads
//        if (readingJob?.isActive == true) {
//            Log.d(TAG, "Already processing a card, ignoring")
//            return
//        }
//
//        resetCardState()
//
//        showFaceVerificationDialog = true
//
////        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
////        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
//
//        cardTapPlayer?.start()
//
//        readingJob = lifecycleScope.launch {
//            try {
//
//                Log.d(TAG, "Starting SAM card read...")
//
//                val secureCardData = withContext(Dispatchers.IO) {
//                    samCardReader.readSecureCardData(
//                        nfcDevice = nfc,
//                        samPassword = getSAMPassword(),
//                        samKeyIndex = 0x01
//                    )
//                }
//
//                showFaceVerificationDialog = false
//                Log.d(TAG, "SAM card read complete")
//
//                if (!secureCardData.isAuthenticated) {
//                    Toast.makeText(
//                        this@MainActivity,
//                        " Authentication failed! Check SAM Password",
//                        Toast.LENGTH_LONG
//                    ).show()
//                    return@launch
//                }
//
//
//                pendingCardData = secureCardData
//                lastScannedCardId = secureCardData.cardId
//                nfcFaceImage = secureCardData.faceImage
//
//                isMainMenuActive = false
////                currentScreen = Screen.CARD_DETAILS
//
//                // Skip card details screen and go directly to face capture
//                if (checkCameraPermission()) {
//                    currentScreen = Screen.CAMERA_CAPTURE
//                    isFaceVerificationActive = true
//                } else {
//                    // If permission is denied, stay on the main menu
//                    currentScreen = Screen.MAIN_MENU
//                    isMainMenuActive = true
//                    cardReadingEnabled = true
//                }
//
//
//            } catch (e: Exception) {
//                showFaceVerificationDialog = false
//                Log.e(TAG, "Error reading Telpo card", e)
//                Toast.makeText(
//                    this@MainActivity,
//                    "Error reading card: ${e.message}",
//                    Toast.LENGTH_LONG
//                ).show()
//
//            } finally {
//                readingJob = null
//            }
//        }
//    }



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
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Verifying card with face match...",
                        Toast.LENGTH_SHORT
                    ).show()
                }

//                resetCardState()
//                currentScreen = Screen.MAIN_MENU
//                isMainMenuActive = true
//                cardReadingEnabled = true
//                telpoT20DataSource.resume()

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

    private fun resetVerificationResults() {
        // Only reset verification results, keep card data
        capturedFaceBitmap = null
        nfcFaceBitmap = null
        faceMatchResult = null

        Log.d(TAG, "Verification results reset (card data preserved)")
    }

    private fun resetCardState() {
        pendingCardData = null
        accessCardData = null
        lastScannedCardId = null
        nfcFaceImage = null
        capturedFaceBitmap = null
        nfcFaceBitmap = null
        faceMatchResult = null

        showCardReadDialog = false
        showAccessGrantedDialog = false
        showFaceVerificationDialog = false
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this,
            pendingIntent,
            intentFiltersArray,
            techListsArray
        )
        if (isMainMenuActive) {
            cardReadingEnabled = true
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

        cardTapPlayer?.release()
        cardTapPlayer = null


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