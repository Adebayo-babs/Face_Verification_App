package com.example.face_verification_app

import com.common.apiutil.nfc.Nfc
import kotlinx.coroutines.flow.Flow

interface TelpoT20DataSource {
    val tagFlow: Flow<Nfc>
    fun start()
    fun stop()
    fun resume()
}