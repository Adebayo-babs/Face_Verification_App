package com.example.face_verification_app

import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.BlendMode.Companion.Screen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.common.FaceMatchResult
import com.example.neurotecsdklibrary.EnrollFaceScreen
import com.example.neurotecsdklibrary.EnrollFaceViewModel

@Composable
fun AutoCaptureAndMatchScreen(
    nfcFaceImage: ByteArray?,
    onCapturedFaceReady: (Bitmap?) -> Unit,
    onNfcFaceReady: (Bitmap?) -> Unit,
    onMatchResult: (FaceMatchResult) -> Unit,
    onNavigateToResult: () -> Unit
) {
    val viewModel: EnrollFaceViewModel = viewModel()

    // Setup callbacks when screen is composed
    LaunchedEffect(Unit) {
        // When face is captured, automatically match with NFC
        viewModel.onFaceCaptured = { imageBytes, templateBytes ->
            Log.d(TAG, "Face captured callback triggered")

            // Pass captured face bitmap to parent
            onCapturedFaceReady(viewModel.capturedFaceBitmap)

            // Automatically trigger matching with stored NFC face
            nfcFaceImage?.let { nfcFace ->
                viewModel.matchWithNFCFace(nfcFace)
            }
        }

        // When matching completes, navigate to result screen
        viewModel.onMatchComplete = { result ->
            Log.d(TAG, "Match complete: ${result.isMatch}, Score: ${result.score}")

            onCapturedFaceReady(viewModel.capturedFaceBitmap)
            onNfcFaceReady(viewModel.nfcFaceBitmap)
            onMatchResult(result)

            // Navigate to result screen
            onNavigateToResult()
        }

        // Set the NFC face data for automatic matching
        nfcFaceImage?.let { nfcFace ->
            viewModel.setNFCCardData(nfcFace)
        }
    }

    // Show the camera capture screen
    EnrollFaceScreen(viewModel = viewModel)
}