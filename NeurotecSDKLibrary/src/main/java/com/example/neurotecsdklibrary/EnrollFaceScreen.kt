package com.example.neurotecsdklibrary

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.common.CommonConstants
import com.common.apiutil.pos.CommonUtil
import com.neurotec.lang.NCore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollFaceScreen(
    viewModel: EnrollFaceViewModel = viewModel()
) {

    val context = LocalContext.current
    val status = viewModel.status
    val feedback = viewModel.detectionFeedback

    val commonUtil = remember { CommonUtil(context) }
    var isFlashOn by remember { mutableStateOf(false) }

    fun toggleFlashlight() {
        try {
            if (isFlashOn) {
                commonUtil.setColorLed(CommonConstants.LedType.FILL_LIGHT_1, CommonConstants.LedColor.WHITE_LED, 0)
                isFlashOn = false
            }else {
                commonUtil.setColorLed(CommonConstants.LedType.FILL_LIGHT_1, CommonConstants.LedColor.WHITE_LED, 255)
                isFlashOn = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (isFlashOn) {
                    commonUtil.setColorLed(CommonConstants.LedType.FILL_LIGHT_1, CommonConstants.LedColor.WHITE_LED, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        NCore.setContext(context)
        viewModel.initialize()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Face Verification") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1C1C1C),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1C1C1C))
            ) {
                SimpleCameraPreview(modifier = Modifier.fillMaxSize())

                // Flashlight toggle button - Top right
                FloatingActionButton(
                    onClick = { toggleFlashlight() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    containerColor = if (isFlashOn) Color(0xFFFFC107) else Color(0xFF424242),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.PlayArrow else Icons.Default.Clear,
                        contentDescription = if (isFlashOn) "Turn off flashlight" else "Turn on flashlight",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Real-time Feedback Overlay - Now at bottom
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Main instruction card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = feedback.overallMessage,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Individual feedback items in a compact grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Lighting feedback
                                CompactFeedbackItem(
                                    label = "Light",
                                    status = when (feedback.lightingStatus) {
                                        LightingStatus.GOOD -> "✓"
                                        LightingStatus.TOO_DARK -> "Dark"
                                        LightingStatus.TOO_BRIGHT -> "Bright"
                                        LightingStatus.UNKNOWN -> "..."
                                    },
                                    isGood = feedback.lightingStatus == LightingStatus.GOOD,
                                    isUnknown = feedback.lightingStatus == LightingStatus.UNKNOWN,
                                    modifier = Modifier.weight(1f)
                                )

                                // Distance feedback
                                CompactFeedbackItem(
                                    label = "Distance",
                                    status = when (feedback.distanceStatus) {
                                        DistanceStatus.GOOD -> "✓"
                                        DistanceStatus.TOO_FAR -> "Far"
                                        DistanceStatus.TOO_CLOSE -> "Close"
                                        DistanceStatus.UNKNOWN -> "..."
                                    },
                                    isGood = feedback.distanceStatus == DistanceStatus.GOOD,
                                    isUnknown = feedback.distanceStatus == DistanceStatus.UNKNOWN,
                                    modifier = Modifier.weight(1f)
                                )

                                // Position feedback
                                CompactFeedbackItem(
                                    label = "Position",
                                    status = when (feedback.positionStatus) {
                                        PositionStatus.CENTERED -> "✓"
                                        PositionStatus.MOVE_LEFT -> "←"
                                        PositionStatus.MOVE_RIGHT -> "→"
                                        PositionStatus.MOVE_UP -> "↑"
                                        PositionStatus.MOVE_DOWN -> "↓"
                                        PositionStatus.UNKNOWN -> "..."
                                    },
                                    isGood = feedback.positionStatus == PositionStatus.CENTERED,
                                    isUnknown = feedback.positionStatus == PositionStatus.UNKNOWN,
                                    modifier = Modifier.weight(1f)
                                )

                                // Quality feedback
                                CompactFeedbackItem(
                                    label = "Quality",
                                    status = when (feedback.qualityStatus) {
                                        QualityStatus.EXCELLENT -> "✓✓"
                                        QualityStatus.GOOD -> "✓"
                                        QualityStatus.FAIR -> "Fair"
                                        QualityStatus.POOR -> "Poor"
                                        QualityStatus.UNKNOWN -> "..."
                                    },
                                    isGood = feedback.qualityStatus == QualityStatus.EXCELLENT ||
                                            feedback.qualityStatus == QualityStatus.GOOD,
                                    isUnknown = feedback.qualityStatus == QualityStatus.UNKNOWN,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Status message
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        status.contains("error", ignoreCase = true) -> Color(0xFFD32F2F)
                        status.contains("success", ignoreCase = true) ||
                                status.contains("captured", ignoreCase = true) -> Color(0xFF4CAF50)
                        status.contains("matching", ignoreCase = true) ||
                                status.contains("processing", ignoreCase = true) -> Color(0xFF2196F3)
                        else -> Color(0xFF2C2C2C)
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (status.contains("matching", ignoreCase = true) ||
                        status.contains("processing", ignoreCase = true)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Text(
                        text = status.ifEmpty { "Initializing camera..." },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompactFeedbackItem(
    label: String,
    status: String,
    isGood: Boolean,
    isUnknown: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isUnknown -> Color(0xFF424242).copy(alpha = 0.7f)
                isGood -> Color(0xFF1B5E20).copy(alpha = 0.8f)
                else -> Color(0xFFB71C1C).copy(alpha = 0.8f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}