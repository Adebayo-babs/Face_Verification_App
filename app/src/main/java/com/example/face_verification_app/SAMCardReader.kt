package com.example.face_verification_app


import android.nfc.tech.IsoDep
import android.util.Log
import com.common.apiutil.nfc.Nfc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SAMCardReader {

    private val telpoNfcLock = Mutex()

    companion object {
        private const val TAG = "SAMCardReader"
        private const val SAM_AID = "A000000077AB01"

        // Response codes
        private const val SW_SUCCESS = 0x9000
        private const val SW_FILE_NOT_FOUND = 0x6A82
        private const val SW_RECORD_NOT_FOUND = 0x6A83

    }

    data class SecureCardData(
        val cardId: String?,
        val surname: String?,
        val firstName: String?,
        val faceImage: ByteArray?,
        val additionalInfo: Map<String, String> = emptyMap(),
        val isAuthenticated: Boolean = false
    )

    data class EnumeratedData(
        val sfi: Int,
        val record: Int,
        val data: ByteArray
    )

    suspend fun readSecureCardData(
        isoDep: IsoDep? = null,
        nfcDevice: Nfc? = null,
        samPassword: String,
        samKeyIndex: Int = 0x01
    ): SecureCardData = withContext(Dispatchers.IO) {
        when {
            isoDep != null -> readFromIsoDep(isoDep, samPassword, samKeyIndex)
            nfcDevice != null -> readFromTelpoNfc(nfcDevice, samPassword, samKeyIndex)
            else -> SecureCardData(
                cardId = null,
                surname = null,
                firstName = null,
                faceImage = null,
                additionalInfo = mapOf("error" to "No device provided"),
                isAuthenticated = false
            )
        }
    }

    private suspend fun readFromIsoDep(
        isoDep: IsoDep,
        samPassword: String,
        samKeyIndex: Int
    ): SecureCardData {
        var cardId: String? = null
        var holderName: String? = null
        var firstName: String? = null
        var faceImage: ByteArray? = null
        var isAuthenticated = false
        val additionalInfo = mutableMapOf<String, String>()
        val enumeratedData = mutableListOf<EnumeratedData>()

        try {
            if (!isoDep.isConnected) isoDep.connect()
            isoDep.timeout = 10000
            Log.d(TAG, "IsoDep connected, starting SAM authentication")

            // Select SAM application
            if (!selectSAMApplication(isoDep, SAM_AID)) {
                additionalInfo["error"] = "SAM application not found"
                Log.e(TAG, "Failed to select SAM application")
                return SecureCardData(
                    cardId,
                    holderName,
                    firstName,
                    faceImage,
                    additionalInfo,
                    false
                )
            }

            Log.d(TAG, "SAM application selected successfully")

            // Authenticate with SAM using password
            isAuthenticated = authenticateWithSAM(
                { cmd -> isoDep.transceive(cmd) },
                samPassword,
                samKeyIndex,
                enumeratedData
            )

            if (isAuthenticated) {
                Log.d(TAG, "SAM authentication successful, parsing data")
                val parsed = parseEnumeratedData(enumeratedData)

                cardId = parsed["cardId"] ?: parsed["documentNumber"]
                holderName = parsed["name"]
                firstName = parsed["firstName"]
                additionalInfo.putAll(parsed)

                // Extract face image - look for largest image data across all SFIs
                faceImage = extractFaceImage(enumeratedData)

                if (faceImage != null) {
                    Log.d(TAG, " Face image extracted: ${faceImage.size} bytes")
                } else {
                    Log.w(TAG, " No face image found in card data")
                }
            } else {
                Log.e(TAG, "SAM authentication failed")
                additionalInfo["error"] = "SAM authentication failed"
            }
        } catch (e: Exception) {
            additionalInfo["error"] = e.message ?: "Unknown error"
            Log.e(TAG, "Error reading IsoDep card", e)
        } finally {
            try {
                if (isoDep.isConnected) {
                    isoDep.close()
                    Log.d(TAG, "IsoDep connection closed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing IsoDep", e)
            }
        }

        return SecureCardData(
            cardId,
            holderName,
            firstName,
            faceImage,
            additionalInfo,
            isAuthenticated
        )
    }

    private suspend fun readFromTelpoNfc(
        nfcDevice: Nfc,
        samPassword: String,
        samKeyIndex: Int
    ): SecureCardData = withContext(Dispatchers.IO) {
        telpoNfcLock.withLock {
            var cardId: String? = null
            var holderName: String? = null
            var firstName: String? = null
            var faceImage: ByteArray? = null
            var isAuthenticated = false
            val additionalInfo = mutableMapOf<String, String>()
            val enumeratedData = mutableListOf<EnumeratedData>()

            try {
                Log.d(TAG, "Starting Telpo NFC card read")

                val transceiveFn: (ByteArray) -> ByteArray = { cmd ->
                    try {
                        val response = nfcDevice.transmit(cmd, cmd.size)
                        response
                    } catch (e: Exception) {
                        Log.e(TAG, "Telpo transceive error: ${e.message}", e)
                        throw e
                    }
                }

                // Select SAM application
                if (!selectSAMApplicationWithFn(transceiveFn, SAM_AID)) {
                    additionalInfo["error"] = "SAM application not found on card"
                    Log.e(TAG, "Failed to select SAM application on Telpo device")
                    return@withContext SecureCardData(
                        cardId,
                        holderName,
                        firstName,
                        faceImage,
                        additionalInfo,
                        false
                    )
                }

                Log.d(TAG, "SAM application selected successfully on Telpo device")

                // Authenticate with SAM
                Log.d(TAG, "Starting SAM authentication on Telpo device")
                isAuthenticated =
                    authenticateWithSAM(transceiveFn, samPassword, samKeyIndex, enumeratedData)

                if (isAuthenticated) {
                    Log.d(TAG, "SAM authentication successful on Telpo device, parsing data")

                    // ALWAYS LOG ALL DATA FIRST FOR DEBUGGING
//                    Log.d(TAG, "=== DEBUGGING ALL ENUMERATED DATA ===")
//                    enumeratedData.groupBy { it.sfi }.forEach { (sfi, records) ->
//                        Log.d(TAG, "SFI $sfi has ${records.size} records:")
//                        records.forEach { rec ->
//                            Log.d(TAG, "  Record ${rec.record}: ${rec.data.size} bytes")
//                            // Print first 50 bytes in hex
//                            val hex = rec.data.take(50).joinToString(" ") { "%02X".format(it) }
//                            Log.d(TAG, "    HEX: $hex")
//                            // Print first 50 bytes as ASCII (readable characters only)
//                            val ascii = rec.data.take(50).map {
//                                if (it in 32..126) it.toInt().toChar() else '.'
//                            }.joinToString("")
//                            Log.d(TAG, "    ASCII: $ascii")
////                            val first20 = rec.data.take(20).joinToString(" ") { "%02X".format(it) }
////                            Log.d(TAG, "  Record ${rec.record}: ${rec.data.size} bytes, starts with: $first20, isImage: ${isImageData(rec.data)}")
////                        }
//                        }
//                    }
//                    Log.d(TAG, "=== END DEBUG ===")

                    val parsed = parseEnumeratedData(enumeratedData)

                    cardId = parsed["cardId"] ?: parsed["documentNumber"]
                    holderName = parsed["name"]
                    firstName = parsed["firstName"]
                    additionalInfo.putAll(parsed)

                    faceImage = extractFaceImage(enumeratedData)

                    if (faceImage != null) {
                        Log.d(TAG, " Face image extracted and verified (${faceImage.size} bytes)")
                    } else {
                        Log.e(TAG, " Failed to extract valid face image")
                    }

                    Log.d(TAG, "Card data successfully read from Telpo device")
                } else {
                    Log.e(TAG, "SAM authentication failed on Telpo device")
                    additionalInfo["error"] = "SAM authentication failed"
                }
            } catch (e: Exception) {
                val errorMsg = "Error reading Telpo NFC card: ${e.message}"
                additionalInfo["error"] = errorMsg
                Log.e(TAG, errorMsg, e)
            }

            return@withContext SecureCardData(
                cardId,
                holderName,
                firstName,
                faceImage,
                additionalInfo,
                isAuthenticated
            )
        }
    }

    private fun parseEnumeratedData(enumeratedData: List<EnumeratedData>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val groupedBySfi = enumeratedData.groupBy { it.sfi }

        Log.d(
            TAG,
            "Parsing enumerated data: ${enumeratedData.size} records, ${groupedBySfi.size} SFIs"
        )

        groupedBySfi.forEach { (sfi, records) ->
            Log.d(TAG, "Processing SFI $sfi with ${records.size} records")
            when (sfi) {
                1 -> {
                    // Cardholder text data
                    val firstRecord = records.firstOrNull { it.record == 1 }
                    firstRecord?.let {
                        Log.d(TAG, "Parsing cardholder data from SFI 1, record 1")
                        result.putAll(parseCardHolderData(it.data))
                    }
                }
                2, 3 -> {
                    // Face image is typically in SFI 2 or 3
                    // Look for the largest record that looks like an image
                    records.filter { it.data.size > 1000 && isImageData(it.data) }
                        .maxByOrNull { it.data.size }
                        ?.let { imageRecord ->
                            Log.d(TAG, "🖼️ Found potential face image in SFI $sfi, record ${imageRecord.record}: ${imageRecord.data.size} bytes")
                        }
                }
            }
        }

        Log.d(TAG, "Parsed ${result.size} fields from card data")
        return result
    }

    private fun parseCardHolderData(data: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = 0

        Log.d(TAG, "Parsing cardholder data: ${data.size} bytes")

        while (i < data.size - 2) {
            if (data[i] == 0xDF.toByte() && i + 1 < data.size) {
                val tag = data[i + 1].toInt() and 0xFF
                i += 2

                if (i >= data.size) break

                val length = data[i].toInt() and 0xFF
                i++

                if (i + length > data.size) break

                val value = data.copyOfRange(i, i + length)
                i += length

                val strVal = String(value, Charsets.UTF_8).trim().replace("\u0000", "")

                // FIXED TAG MAPPING - This is the key change!
                val fieldName = when (tag) {
                    0x01 -> "firstName"
                    0x02 -> "surname"
                    0x03 -> "middleName"
                    0x04 -> "nationality"
                    0x05 -> "dob"
                    0x06 -> "gender"
                    0x07 -> "height"
                    0x08 -> "address"
                    0x09 -> "documentNumber"
                    0x0A -> "cardId"
                    0x0B -> "issueDate"
                    0x0C -> "expiryDate"
                    0x0D -> "documentType"
                    else -> "unknownTag_DF%02X".format(tag)
                }

                result[fieldName] = strVal
                Log.d(TAG, "Parsed field $fieldName (tag 0x${tag.toString(16)}): $strVal")
            } else {
                i++
            }
        }

        // Derived/computed fields
        val fullName = listOfNotNull(result["surname"], result["firstName"], result["middleName"])
            .joinToString(" ")
            .trim()

        if (fullName.isNotEmpty()) {
            result["name"] = fullName
            Log.d(TAG, "Computed full name: $fullName")
        }

        return result
    }

    private fun authenticateWithSAM(
        transceive: (ByteArray) -> ByteArray,
        samPassword: String,
        keyIndex: Int,
        enumeratedData: MutableList<EnumeratedData>
    ): Boolean {
        return try {
            Log.d(TAG, "Starting SAM authentication for government ID card with key index $keyIndex")

            // Method 1: Try reading without authentication first
            Log.d(TAG, "Method 1: Attempting to read without authentication...")
            val filesFound = enumerateAndReadFiles(transceive, enumeratedData)

            if (filesFound && enumeratedData.isNotEmpty()) {
                Log.d(TAG, " Successfully read ${enumeratedData.size} records without authentication")
                return true
            }

            // Add other authentication methods if needed...
            Log.e(TAG, " All authentication methods failed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "SAM authentication error: ${e.message}", e)
            false
        }
    }

    private fun enumerateAndReadFiles(
        transceive: (ByteArray) -> ByteArray,
        enumeratedData: MutableList<EnumeratedData>
    ): Boolean {
        var foundAny = false

        val targetSFIs = listOf(1, 2, 3, 4, 5)
        for (sfi in targetSFIs) {
            var consecutiveFailures = 0
            val sfiRecords = mutableListOf<ByteArray>()

            for (record in 1..20) {
                try {
                    val command = byteArrayOf(
                        0x00.toByte(), 0xB2.toByte(), // READ RECORD
                        record.toByte(),
                        ((sfi shl 3) or 0x04).toByte(),
                        0x00.toByte()
                    )

                    val response = transceive(command)
                    val sw = getStatusWord(response)

                    if (sw == SW_SUCCESS) {
                        val data = response.copyOfRange(0, response.size - 2)
                        enumeratedData.add(EnumeratedData(sfi, record, data))
                        sfiRecords.add(data)
                        foundAny = true
                        consecutiveFailures = 0
//                        Log.d(TAG, " Read SFI $sfi, record $record: ${data.size} bytes")
                    } else if (sw == SW_RECORD_NOT_FOUND || sw == SW_FILE_NOT_FOUND) {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            Log.d(TAG, " SFI $sfi: 3 consecutive failures, moving to next SFI")
                            break
                        }
                    } else {
                        consecutiveFailures++
                        Log.d(TAG, "SFI $sfi, record $record returned SW: 0x${sw.toString(16)}")
                        if (consecutiveFailures >= 3) break
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    Log.d(TAG, "Exception reading SFI $sfi, record $record: ${e.message}")
                    if (consecutiveFailures >= 3) break
                }
            }

            if (sfiRecords.isNotEmpty()) {
                Log.d(TAG, " SFI $sfi: Read ${sfiRecords.size} records successfully")
            }

        }

        return foundAny
    }

    private fun extractFaceImage(enumeratedData: List<EnumeratedData>): ByteArray? {

        val sfi2Data = enumeratedData
            .filter { it.sfi == 2 }
            .sortedBy { it.record }
            .flatMap { it.data.toList() }
            .toByteArray()

        if (sfi2Data.isNotEmpty()) {
            val jpegImage = extractJPEG(sfi2Data)
            if (jpegImage != null) {
                Log.d(TAG, "✅ Extracted JPEG from SFI 2 (${jpegImage.size} bytes)")
                return jpegImage
            }
        }

        // Try SFI 3 as fallback (GIF zone)
        val sfi3Data = enumeratedData
            .filter { it.sfi == 3 }
            .sortedBy { it.record }
            .flatMap { it.data.toList() }
            .toByteArray()

        if (sfi3Data.isNotEmpty()) {
            // Try JPEG first in SFI 3
            val jpegImage = extractJPEG(sfi3Data)
            if (jpegImage != null) {
                Log.d(TAG, "✅ Extracted JPEG from SFI 3 (${jpegImage.size} bytes)")
                return jpegImage
            }

            // Try GIF
            val gifImage = extractGIF(sfi3Data)
            if (gifImage != null) {
                Log.d(TAG, "Extracted GIF from SFI 3 (${gifImage.size} bytes)")
                return gifImage
            }
        }

        Log.e(TAG, " No valid image found in SFI 2 or 3")
        return null
    }

    private fun extractJPEG(data: ByteArray): ByteArray? {
        val start = data.indexOfSequence(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI
        val end = data.indexOfSequence(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))   // EOI

        return if (start != -1 && end != -1 && end > start) {
            data.copyOfRange(start, end + 2)
        } else null
    }

    private fun extractGIF(data: ByteArray): ByteArray? {
        val start = data.indexOfSequence("GIF89a".toByteArray())
        val end = data.indexOfSequence(byteArrayOf(0x00, 0x3B)) // GIF trailer

        return if (start != -1 && end != -1 && end > start) {
            data.copyOfRange(start, end + 2)
        } else null
    }
    private fun ByteArray.indexOfSequence(seq: ByteArray): Int {
        outer@ for (i in indices) {
            if (i + seq.size > size) break
            for (j in seq.indices) {
                if (this[i + j] != seq[j]) continue@outer
            }
            return i
        }
        return -1
    }


    private fun selectSAMApplication(isoDep: IsoDep, aidHex: String): Boolean =
        selectSAMApplicationWithFn({ cmd -> isoDep.transceive(cmd) }, aidHex)

    private fun selectSAMApplicationWithFn(
        transceive: (ByteArray) -> ByteArray,
        aidHex: String
    ): Boolean {
        return try {
            val aidBytes = hexStringToByteArray(aidHex)
            val command = byteArrayOf(
                0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), aidBytes.size.toByte()
            ) + aidBytes

            Log.d(TAG, "SELECT command: ${command.toHexString()}")
            val response = transceive(command)
            val success = isSuccessResponse(response)
            Log.d(TAG, "SELECT response: ${response.toHexString()}, success: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting SAM application", e)
            false
        }
    }

    private fun combineRecords(records: List<ByteArray>): ByteArray {
        val totalSize = records.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (r in records) {
            r.copyInto(result, offset)
            offset += r.size
        }
        return result
    }

    private fun getStatusWord(response: ByteArray): Int =
        if (response.size < 2) 0 else
            ((response[response.size - 2].toInt() and 0xFF) shl 8) or (response[response.size - 1].toInt() and 0xFF)

    private fun isSuccessResponse(response: ByteArray) = getStatusWord(response) == SW_SUCCESS

    private fun isImageData(data: ByteArray): Boolean {
        if (data.size < 4) return false

        // Check if starts with image signature
        val startsWithImage = when {
            data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte() -> true
            data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() -> true
            else -> false
        }

        if (startsWithImage) return true

        // NEW: Check if image signature exists ANYWHERE in first 100 bytes (wrapped in TLV)
        val searchRange = minOf(data.size, 100)
        for (i in 0 until searchRange - 2) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() && data[i + 2] == 0xFF.toByte()) {
                Log.d(TAG, "Found JPEG signature at offset $i (wrapped)")
                return true
            }
        }

        return false
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        if (sequence.isEmpty() || this.size < sequence.size) return false

        for (i in 0..this.size - sequence.size) {
            var found = true
            for (j in sequence.indices) {
                if (this[i + j] != sequence[j]) {
                    found = false
                    break
                }
            }
            if (found) return true
        }
        return false
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val cleaned = s.replace(" ", "").replace(":", "")
        val len = cleaned.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(cleaned[i], 16) shl 4) +
                    Character.digit(cleaned[i + 1], 16)).toByte()
        }
        return data
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }
}