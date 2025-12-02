package com.example.face_verification_app.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aratek.trustfinger.common.CommonUtil
import com.common.CommonConstants
import com.example.common.FaceMatchResult
import com.example.face_verification_app.MainActivity
import com.example.face_verification_app.SAMCardReader
import com.example.neurotecsdklibrary.EnrollFaceViewModel
import kotlinx.coroutines.delay

@Composable
fun FaceVerificationResultScreen(
    capturedFace: Bitmap?,
    nfcFace: Bitmap?,
    matchResult: FaceMatchResult?,
    cardData: SAMCardReader.SecureCardData?,
    onRetakePhoto: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {



    BackHandler {
        onCancel()
    }

    val context = LocalContext.current
    val commonUtil = com.common.apiutil.pos.CommonUtil(context)

    // Track if relay has been activated
    var relayActivated by remember { mutableStateOf(false) }

    LaunchedEffect(matchResult?.isMatch) {
        if (matchResult?.isMatch == true && !relayActivated) {

            Log.d("FaceVerification", "Activating relay")

            // Turn ON the relay
            commonUtil.setRelayPower(CommonConstants.RelayType.RELAY_1, 1)
            relayActivated = true

            // Wait 2 seconds
            delay(2000)

            // Turn OFF the relay
            commonUtil.setRelayPower(CommonConstants.RelayType.RELAY_1, 0)
            relayActivated = false
            Log.d("FaceVerification", "Relay turned off after 2 seconds")
        }
    }

//    Log.v("Relay result", relay.toString())


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Face Verification",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Card information
        if (cardData != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Card ID: ${cardData.cardId}",
                        fontWeight = FontWeight.Bold
                    )
                    cardData.surname?.let {
                        if (it.isNotBlank()) {
                            Text("Name: $it", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Display faces side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("Live Capture", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (capturedFace != null) {
                    Card(
                        modifier = Modifier.size(120.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Image(
                            bitmap = capturedFace.asImageBitmap(),
                            contentDescription = "Captured Face",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.LightGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Image", fontSize = 12.sp)
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("Card Photo", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (nfcFace != null) {
                    Card(
                        modifier = Modifier.size(120.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Image(
                            bitmap = nfcFace.asImageBitmap(),
                            contentDescription = "NFC Face",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.LightGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Reading...", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Match result
        if (matchResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (matchResult.isMatch)
                        Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (matchResult.isMatch) {
                            " FACES MATCH"
                        } else {
                            " FACES DO NOT MATCH"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

//                    Text(
//                        text = "Similarity Score: ${matchResult.score}%",
//                        fontSize = 20.sp,
//                        color = Color.White
//                    )
//
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    Text(
//                        text = "Threshold: ${matchResult.threshold}%",
//                        fontSize = 14.sp,
//                        color = Color.White.copy(alpha = 0.8f)
//                    )
//
//                    Spacer(modifier = Modifier.height(16.dp))
//
//                    Text(
//                        text = when {
//                            matchResult.score >= 70 -> "High Confidence Match"
//                            matchResult.score >= matchResult.threshold -> "Match Confirmed"
//                            matchResult.score >= 40 -> "Low Confidence"
//                            else -> "No Match Detected"
//                        },
//                        fontSize = 16.sp,
//                        color = Color.White.copy(alpha = 0.9f)
//                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing faces...",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action buttons
        if (matchResult != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!matchResult.isMatch) {
                    OutlinedButton(
                        onClick = onRetakePhoto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("Retake Photo", fontSize = 18.sp)
                    }
                }

                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(" Scan Another Card", fontSize = 18.sp)
                }

            }
        } else {
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Cancel", fontSize = 18.sp)
            }
        }
    }
}