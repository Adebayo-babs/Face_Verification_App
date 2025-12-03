package com.example.neurotecsdklibrary


import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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

    val commonUtil = remember { CommonUtil(context) }

    // Automatically turn on flashlight when screen appears
    LaunchedEffect(viewModel.useNeurotecCamera) {
        try {
            if (viewModel.useNeurotecCamera) {
                commonUtil.setColorLed(CommonConstants.LedType.FILL_LIGHT_1, CommonConstants.LedColor.WHITE_LED, 255)
                Log.d("EnrollFaceScreen", "Flashlight turned ON")
            }else {
                commonUtil.setColorLed(CommonConstants.LedType.FILL_LIGHT_1, CommonConstants.LedColor.WHITE_LED, 0)
                Log.d("EnrollFaceScreen", "Flashlight turned OFF")
            }
        } catch (e: Exception) {
            Log.e("EnrollFaceScreen", "Error turning on flashlight", e)
        }
    }

    // Turn off flashlight when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            try {
                commonUtil.setColorLed(CommonConstants.LedType.FILL_LIGHT_1, CommonConstants.LedColor.WHITE_LED, 0)
                Log.d("EnrollFaceScreen", "Flashlight turned OFF")
            } catch (e: Exception) {
                Log.e("EnrollFaceScreen", "Error turning off flashlight", e)
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
//                SimpleCameraPreview(
//                    modifier = Modifier.fillMaxSize(),
//                    viewModel = viewModel
//                )

                if (viewModel.useNeurotecCamera) {
                    SimpleCameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel
                    )
                } else {
                    SimpleCameraPreviewGrayScale(
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Camera switch button
                    FloatingActionButton(
                        onClick = {
                            Log.d("EnrollFaceScreen", "CAMERA SWITCH BUTTON CLICKED ")
                            viewModel.toggleCameraPreview()

                            Toast.makeText(
                                context,
                                if (viewModel.useNeurotecCamera) "Switched to Colored" else "Switched to Camera Grayscale",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        containerColor = Color(0xFF424242),
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Switch camera",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Flashlight toggle button
//                    FloatingActionButton(
//                        onClick = { toggleFlashlight() },
//                        containerColor = if (isFlashOn) Color(0xFFFFC107) else Color(0xFF424242),
//                        contentColor = Color.White,
//                        modifier = Modifier.size(48.dp)
//                    ) {
//                        Icon(
//                            imageVector = if (isFlashOn) Icons.Default.PlayArrow else Icons.Default.Clear,
//                            contentDescription = if (isFlashOn) "Turn off flashlight" else "Turn on flashlight",
//                            modifier = Modifier.size(24.dp)
//                        )
//                    }
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
