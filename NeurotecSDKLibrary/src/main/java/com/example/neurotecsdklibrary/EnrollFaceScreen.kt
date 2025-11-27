package com.example.neurotecsdklibrary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.remember
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neurotec.lang.NCore
import com.neurotec.biometrics.view.NFaceView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollFaceScreen(
    viewModel: EnrollFaceViewModel = viewModel()
) {

//    val showDialog = remember { mutableStateOf(false) }
//
//    BackHandler {
//        showDialog.value = true
//    }
//
//    if (showDialog.value) {
//        AlertDialog(
//            onDismissRequest = { showDialog.value = false },
//            title = { Text("Exit Enrollment?") },
//            text = { Text("Are you sure you want to stop face enrollment?") },
//            confirmButton = {
//                TextButton(onClick = {
//                    showDialog.value = false
//                    // exit the screen
//                }) { Text("Yes") }
//            },
//            dismissButton = {
//                TextButton(onClick = { showDialog.value = false }) { Text("No") }
//            }
//        )
//    }

    val context = LocalContext.current
    val status = viewModel.status

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

                // Overlay instructions
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Face the camera",
                                color = Color.White,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Status message
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        status.contains("error", ignoreCase = true) -> Color(0xFFD32F2F)
                        status.contains("success", ignoreCase = true) -> Color(0xFF388E3C)
                        status.contains("captured", ignoreCase = true) -> Color(0xFF4CAF50)
                        status.contains("matching", ignoreCase = true) -> Color(0xFF1976D2)
                        else -> Color(0xFF2C2C2C)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (status.contains("matching", ignoreCase = true) ||
                        status.contains("processing", ignoreCase = true)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = status.ifEmpty { "Initializing camera..." },
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }


}