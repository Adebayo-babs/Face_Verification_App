package com.example.common

data class FaceMatchResult(
    val capturedFace: ByteArray,
    val nfcFace: ByteArray?,
    val score: Int,
    val threshold: Int = 70,
) {
    val isMatch get() = score >= threshold
}