package com.example.face_verification_app

import android.content.Context
import android.util.Log
import com.common.apiutil.nfc.Nfc
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class TelpoT20DataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
): TelpoT20DataSource {

    private var cardReadingJob: Job? = null
    private val isReadingCard = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    private var currentNfc: Nfc? = null
    private var lastReadTime = 0L

    private val _nfcTagFlow = MutableSharedFlow<Nfc>(replay = 0)
    override val tagFlow: SharedFlow<Nfc> = _nfcTagFlow.asSharedFlow()

    override fun start() {
        if (isReadingCard.get()) {
            Log.d("TelpoNFC", "Already reading, ignoring start")
            return
        }

        isReadingCard.set(true)
        isProcessing.set(false)

        cardReadingJob = CoroutineScope(Dispatchers.IO).launch {
            val nfc = Nfc(context)
            currentNfc = nfc

            try {
                nfc.close()
                nfc.open()
                Log.d("TelpoNFC", "NFC device opened, starting detection loop")

                while (isActive && isReadingCard.get()) {
                    // Skip detection if we're processing a card
                    if (isProcessing.get()) {
                        delay(200)
                        continue
                    }

                    try {
                        // Use longer timeout to support SAM card reading
                        Log.d("TelpoNFC", "Waiting for card tap...")
                        val tag = nfc.activate(2000)  // Shorter timeout for faster re-detection

                        if (tag != null) {
                            // Check if enough time has passed since last read (debounce)
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastRead = currentTime - lastReadTime

                            if (timeSinceLastRead < 1000) {
                                Log.d("TelpoNFC", "Ignoring duplicate detection (${timeSinceLastRead}ms since last read)")
                                delay(100)
                                continue
                            }

                            Log.d("TelpoNFC", "✅ Card detected! Tag: $tag")
                            lastReadTime = currentTime

                            // Mark as processing to prevent concurrent reads
                            isProcessing.set(true)

                            // Emit the NFC device
                            _nfcTagFlow.emit(nfc)

                            // The connection stays active during SAMCardReader operations
                            Log.d("TelpoNFC", "Card emitted, processing in progress...")

                        } else {
                            // No card detected in this cycle
                            delay(100)
                        }
                    } catch (e: Exception) {
                        Log.w("TelpoNFC", "Detection cycle error: ${e.message}")
                        delay(200)
                    }
                }

            } finally {
                try {
                    nfc.close()
                    currentNfc = null
                    Log.d("TelpoNFC", "NFC device closed")
                } catch (e: Exception) {
                    Log.e("TelpoNFC", "Error closing NFC: ${e.message}")
                }
            }
        }
    }

    override fun stop() {
        Log.d("TelpoNFC", "Stopping NFC detection")
        cardReadingJob?.cancel()
        isReadingCard.set(false)
        isProcessing.set(false)

        currentNfc?.let {
            try {
                it.close()
            } catch (e: Exception) {
                Log.e("TelpoNFC", "Error closing NFC on stop: ${e.message}")
            }
        }
        currentNfc = null
    }

    override fun resume() {
        Log.d("TelpoNFC", "Resume called - ready for next card tap")

        // Immediately clear processing flag
        isProcessing.set(false)

        // If the job isn't active, restart it
        if (cardReadingJob?.isActive != true) {
            Log.d("TelpoNFC", "Restarting detection job")
            start()
        } else {
            Log.d("TelpoNFC", "Detection loop ready for next tap")
        }
    }
}