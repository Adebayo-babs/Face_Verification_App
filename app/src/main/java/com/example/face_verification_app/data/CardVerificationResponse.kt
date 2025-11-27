package com.example.face_verification_app.data

data class CardVerificationResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)
