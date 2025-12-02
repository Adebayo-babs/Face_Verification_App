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

    data class FingerprintData(
        val template: ByteArray?,
        val fingerIndex: Int = 0, // Which finger (1-10)
        val format: String = "UNKNOWN" // ISO, ANSI, WSQ, etc.
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FingerprintData
            if (template != null) {
                if (other.template == null) return false
                if (!template.contentEquals(other.template)) return false
            } else if (other.template != null) return false
            if (fingerIndex != other.fingerIndex) return false
            if (format != other.format) return false
            return true
        }

        override fun hashCode(): Int {
            var result = template?.contentHashCode() ?: 0
            result = 31 * result + fingerIndex
            result = 31 * result + format.hashCode()
            return result
        }
    }

    data class SecureCardData(
        val cardId: String?,
        val surname: String?,
        val firstName: String?,
        val faceImage: ByteArray?,
        val fingerprintData: List<FingerprintData> = emptyList(), // NEW: Support multiple fingerprints
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
                fingerprintData = emptyList(),
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
        val fingerprintData = mutableListOf<FingerprintData>()
        var isAuthenticated = false
        val additionalInfo = mutableMapOf<String, String>()
        val enumeratedData = mutableListOf<EnumeratedData>()

        try {
            if (!isoDep.isConnected) isoDep.connect()
            isoDep.timeout = 10000
            Log.d(TAG, "IsoDep connected, starting SAM authentication")

            if (!selectSAMApplication(isoDep, SAM_AID)) {
                additionalInfo["error"] = "SAM application not found"
                Log.e(TAG, "Failed to select SAM application")
                return SecureCardData(
                    cardId, holderName, firstName, faceImage,
                    fingerprintData, additionalInfo, false
                )
            }

            Log.d(TAG, "SAM application selected successfully")

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

                faceImage = extractFaceImage(enumeratedData)
                fingerprintData.addAll(extractFingerprints(enumeratedData))

                if (faceImage != null) {
                    Log.d(TAG, "Face image extracted: ${faceImage.size} bytes")
                }

                if (fingerprintData.isNotEmpty()) {
                    Log.d(TAG, "Extracted ${fingerprintData.size} fingerprint(s)")
                    fingerprintData.forEachIndexed { idx, fp ->
                        Log.d(TAG, "  Fingerprint $idx: ${fp.template?.size ?: 0} bytes, format: ${fp.format}")
                    }
                } else {
                    Log.w(TAG, "No fingerprint data found")
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
            cardId, holderName, firstName, faceImage,
            fingerprintData, additionalInfo, isAuthenticated
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
            val fingerprintData = mutableListOf<FingerprintData>()
            var isAuthenticated = false
            val additionalInfo = mutableMapOf<String, String>()
            val enumeratedData = mutableListOf<EnumeratedData>()

            try {
                Log.d(TAG, "Starting Telpo NFC card read")

                val transceiveFn: (ByteArray) -> ByteArray = { cmd ->
                    try {
                        nfcDevice.transmit(cmd, cmd.size)
                    } catch (e: Exception) {
                        Log.e(TAG, "Telpo transceive error: ${e.message}", e)
                        throw e
                    }
                }

                if (!selectSAMApplicationWithFn(transceiveFn, SAM_AID)) {
                    additionalInfo["error"] = "SAM application not found on card"
                    Log.e(TAG, "Failed to select SAM application on Telpo device")
                    return@withContext SecureCardData(
                        cardId, holderName, firstName, faceImage,
                        fingerprintData, additionalInfo, false
                    )
                }

                Log.d(TAG, "SAM application selected successfully on Telpo device")
                isAuthenticated = authenticateWithSAM(
                    transceiveFn, samPassword, samKeyIndex, enumeratedData
                )

                if (isAuthenticated) {
                    Log.d(TAG, "SAM authentication successful on Telpo device, parsing data")

                    val parsed = parseEnumeratedData(enumeratedData)
                    cardId = parsed["cardId"] ?: parsed["documentNumber"]
                    holderName = parsed["name"]
                    firstName = parsed["firstName"]
                    additionalInfo.putAll(parsed)

                    faceImage = extractFaceImage(enumeratedData)
                    fingerprintData.addAll(extractFingerprints(enumeratedData))

                    if (faceImage != null) {
                        Log.d(TAG, "Face image extracted (${faceImage.size} bytes)")
                    }

                    if (fingerprintData.isNotEmpty()) {
                        Log.d(TAG, "Extracted ${fingerprintData.size} fingerprint(s)")
                    } else {
                        Log.w(TAG, "No fingerprint data found")
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
                cardId, holderName, firstName, faceImage,
                fingerprintData, additionalInfo, isAuthenticated
            )
        }
    }

    private fun parseEnumeratedData(enumeratedData: List<EnumeratedData>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val groupedBySfi = enumeratedData.groupBy { it.sfi }

        Log.d(TAG, "Parsing enumerated data: ${enumeratedData.size} records, ${groupedBySfi.size} SFIs")

        groupedBySfi.forEach { (sfi, records) ->
            Log.d(TAG, "Processing SFI $sfi with ${records.size} records")
            when (sfi) {
                1 -> {
                    val firstRecord = records.firstOrNull { it.record == 1 }
                    firstRecord?.let {
                        Log.d(TAG, "Parsing cardholder data from SFI 1, record 1")
                        result.putAll(parseCardHolderData(it.data))
                    }
                }
                2, 3 -> {
                    // Face image handling
                    records.filter { it.data.size > 1000 && isImageData(it.data) }
                        .maxByOrNull { it.data.size }
                        ?.let { imageRecord ->
                            Log.d(TAG, "Found potential face image in SFI $sfi, record ${imageRecord.record}")
                        }
                }
                4, 5, 6 -> {
                    // Fingerprint data typically in these SFIs
                    records.filter { it.data.size > 100 }
                        .forEach { fpRecord ->
                            Log.d(TAG, "Potential fingerprint in SFI $sfi, record ${fpRecord.record}: ${fpRecord.data.size} bytes")
                        }
                }
            }
        }

        Log.d(TAG, "Parsed ${result.size} fields from card data")
        return result
    }


    private fun extractFingerprints(enumeratedData: List<EnumeratedData>): List<FingerprintData> {
        val fingerprints = mutableListOf<FingerprintData>()

        // Check SFI 4, 5, 6 for fingerprint data
        val fingerprintSFIs = listOf(4, 5, 6)

        fingerprintSFIs.forEach { sfi ->
            val sfiData = enumeratedData
                .filter { it.sfi == sfi }
                .sortedBy { it.record }
                .flatMap { it.data.toList() }
                .toByteArray()

            if (sfiData.isNotEmpty()) {
                // Try to extract fingerprint template
                val template = extractFingerprintTemplate(sfiData)
                if (template != null) {
                    val format = detectFingerprintFormat(template)
                    fingerprints.add(FingerprintData(
                        template = template,
                        fingerIndex = sfi - 3, // Map SFI to finger index
                        format = format
                    ))
                    Log.d(TAG, "Extracted fingerprint from SFI $sfi (${template.size} bytes, format: $format)")
                }
            }
        }

        return fingerprints
    }


    private fun extractFingerprintTemplate(data: ByteArray): ByteArray? {
        if (data.size < 20) return null

        // Method 1: Look for ISO/IEC 19794-2 format (starts with "FMR")
        val isoTemplate = extractISOFingerprint(data)
        if (isoTemplate != null) return isoTemplate

        // Method 2: Look for ANSI 378 format
        val ansiTemplate = extractANSIFingerprint(data)
        if (ansiTemplate != null) return ansiTemplate

        // Method 3: Look for WSQ compressed format
        val wsqTemplate = extractWSQFingerprint(data)
        if (wsqTemplate != null) return wsqTemplate

        // Method 4: Raw template (just return if size is reasonable)
        if (data.size in 100..10000) {
            Log.d(TAG, "Extracted raw fingerprint template (${data.size} bytes)")
            return data
        }

        return null
    }


    private fun extractISOFingerprint(data: ByteArray): ByteArray? {
        // ISO template starts with "FMR\0" or specific header bytes
        val fmrHeader = byteArrayOf(0x46, 0x4D, 0x52, 0x00) // "FMR\0"
        val start = data.indexOfSequence(fmrHeader)

        if (start != -1) {
            // ISO templates are typically 300-2000 bytes
            val end = minOf(start + 2000, data.size)
            return data.copyOfRange(start, end)
        }

        // Alternative: Check for ISO header pattern (0x464D5200 or similar)
        if (data.size >= 4 &&
            data[0] == 0x46.toByte() &&
            data[1] == 0x4D.toByte() &&
            data[2] == 0x52.toByte()) {
            return data.copyOf()
        }

        return null
    }


    private fun extractANSIFingerprint(data: ByteArray): ByteArray? {
        // ANSI 378 has specific header: Version, Image Size, etc.
        // Typically starts with 0x41, 0x4E (AN) or version bytes
        if (data.size >= 26) { // Minimum ANSI template size
            // Check for ANSI-like structure
            if ((data[0] == 0x00.toByte() || data[0] == 0x01.toByte()) &&
                data.size in 100..5000) {
                return data.copyOf()
            }
        }
        return null
    }


    private fun extractWSQFingerprint(data: ByteArray): ByteArray? {
        // WSQ compressed images start with specific markers
        // Typically 0xFF, 0xA0 (SOI marker for WSQ)
        if (data.size >= 2 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xA0.toByte()) {
            return data.copyOf()
        }
        return null
    }


    private fun detectFingerprintFormat(template: ByteArray): String {
        if (template.size < 4) return "UNKNOWN"

        return when {
            // ISO/IEC 19794-2
            template[0] == 0x46.toByte() &&
                    template[1] == 0x4D.toByte() &&
                    template[2] == 0x52.toByte() -> "ISO_19794_2"

            // WSQ
            template[0] == 0xFF.toByte() &&
                    template[1] == 0xA0.toByte() -> "WSQ"

            // ANSI 378
            template.size in 100..5000 &&
                    (template[0] == 0x00.toByte() || template[0] == 0x01.toByte()) -> "ANSI_378"

            // Raw/Unknown
            else -> "RAW"
        }
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

            val filesFound = enumerateAndReadFiles(transceive, enumeratedData)

            if (filesFound && enumeratedData.isNotEmpty()) {
                Log.d(TAG, "Successfully read ${enumeratedData.size} records without authentication")
                return true
            }

            Log.e(TAG, "All authentication methods failed")
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

        // Extended SFI range to include fingerprint data (4-6)
        val targetSFIs = listOf(1, 2, 3, 4, 5, 6)
        for (sfi in targetSFIs) {
            var consecutiveFailures = 0
            val sfiRecords = mutableListOf<ByteArray>()

            for (record in 1..20) {
                try {
                    val command = byteArrayOf(
                        0x00.toByte(), 0xB2.toByte(),
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
                    } else if (sw == SW_RECORD_NOT_FOUND || sw == SW_FILE_NOT_FOUND) {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            Log.d(TAG, "SFI $sfi: 3 consecutive failures, moving to next SFI")
                            break
                        }
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) break
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) break
                }
            }

            if (sfiRecords.isNotEmpty()) {
                Log.d(TAG, "SFI $sfi: Read ${sfiRecords.size} records successfully")
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
                Log.d(TAG, "Extracted JPEG from SFI 2 (${jpegImage.size} bytes)")
                return jpegImage
            }
        }

        val sfi3Data = enumeratedData
            .filter { it.sfi == 3 }
            .sortedBy { it.record }
            .flatMap { it.data.toList() }
            .toByteArray()

        if (sfi3Data.isNotEmpty()) {
            val jpegImage = extractJPEG(sfi3Data)
            if (jpegImage != null) {
                Log.d(TAG, "Extracted JPEG from SFI 3 (${jpegImage.size} bytes)")
                return jpegImage
            }

            val gifImage = extractGIF(sfi3Data)
            if (gifImage != null) {
                Log.d(TAG, "Extracted GIF from SFI 3 (${gifImage.size} bytes)")
                return gifImage
            }
        }

        Log.e(TAG, "No valid image found in SFI 2 or 3")
        return null
    }

    private fun extractJPEG(data: ByteArray): ByteArray? {
        val start = data.indexOfSequence(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        val end = data.indexOfSequence(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))

        return if (start != -1 && end != -1 && end > start) {
            data.copyOfRange(start, end + 2)
        } else null
    }

    private fun extractGIF(data: ByteArray): ByteArray? {
        val start = data.indexOfSequence("GIF89a".toByteArray())
        val end = data.indexOfSequence(byteArrayOf(0x00, 0x3B))

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

    private fun getStatusWord(response: ByteArray): Int =
        if (response.size < 2) 0 else
            ((response[response.size - 2].toInt() and 0xFF) shl 8) or
                    (response[response.size - 1].toInt() and 0xFF)

    private fun isSuccessResponse(response: ByteArray) = getStatusWord(response) == SW_SUCCESS

    private fun isImageData(data: ByteArray): Boolean {
        if (data.size < 4) return false

        val startsWithImage = when {
            data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte() -> true
            data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
                    data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() -> true
            else -> false
        }

        if (startsWithImage) return true

        val searchRange = minOf(data.size, 100)
        for (i in 0 until searchRange - 2) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() &&
                data[i + 2] == 0xFF.toByte()) {
                return true
            }
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