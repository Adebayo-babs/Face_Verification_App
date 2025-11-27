package com.example.face_verification_app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.face_verification_app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateToMain: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var loadingComplete by remember { mutableStateOf(false) }

    // Animations
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.5f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(50f) }
    val progressAlpha = remember { Animatable(0f) }
    val badgeAlpha = remember { Animatable(0f) }
    val badgeOffsetY = remember { Animatable(-30f) }
    val featuresAlpha = remember { Animatable(0f) }

    // Infinite animations
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Lottie animation (optional - you can remove if not needed)
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.nfc_splash))
    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    LaunchedEffect(Unit) {
        // Stage 1: Badge appears (0-500ms)
        launch {
            badgeAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
        launch {
            badgeOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }

        delay(200)

        // Stage 2: Logo appears (500-1300ms)
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        delay(400)

        // Stage 3: Text appears (900-1500ms)
        launch {
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
        launch {
            textOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }

        delay(200)

        // Stage 4: Progress bar appears (1100-1700ms)
        progressAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )

        delay(200)

        // Stage 5: Features appear (1300-1900ms)
        featuresAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )

        // Stage 6: Loading progress (2000-4000ms)
        while (progress < 1f) {
            delay(30)
            progress += 0.015f
        }

        loadingComplete = true
        delay(500)

        // Navigate to main
        onNavigateToMain()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF006400), // Dark green
                        Color(0xFF00A86B), // Medium green
                        Color(0xFF00CED1)  // Cyan accent
                    )
                )
            )
    ) {
        // Animated background blobs
        AnimatedBackgroundBlobs()

        // Floating particles
        FloatingParticles()

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top badge
            Box(
                modifier = Modifier
                    .offset(y = badgeOffsetY.value.dp)
                    .alpha(badgeAlpha.value)
            ) {
                SecurityBadge()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logo container with NFC waves
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                // Rotating glow ring
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .rotate(rotation)
                        .blur(20.dp)
                        .alpha(0.3f)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF00FF7F),
                                    Color(0xFF00CED1),
                                    Color(0xFF00FF7F)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // NFC wave animations
                NFCWaveAnimation()

                // Center circle with icon
                Surface(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulse),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 24.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Optional: Use Lottie animation
                        if (composition != null) {
                            LottieAnimation(
                                composition = composition,
                                progress = { lottieProgress },
                                modifier = Modifier.size(120.dp)
                            )
                        } else {
                            // Fallback icon
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Security Shield",
                                modifier = Modifier.size(80.dp),
                                tint = Color(0xFF00A86B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Text content
            Column(
                modifier = Modifier
                    .offset(y = textOffsetY.value.dp)
                    .alpha(textAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Banana Island",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Decorative divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(16.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Access Validator",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Secure NFC & QR Code Authentication",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Progress indicator
            Column(
                modifier = Modifier
                    .alpha(progressAlpha.value)
                    .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color(0xFF00FFD1)
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Loading text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!loadingComplete) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .scale(pulse)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Initializing secure connection...",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF00FFD1)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ready to validate",
                            fontSize = 13.sp,
                            color = Color(0xFF00FFD1),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Feature icons
            Row(
                modifier = Modifier
                    .alpha(featuresAlpha.value),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                FeatureIcon(icon = Icons.Filled.Person, label = "Person")
                FeatureIcon(icon = Icons.Filled.Lock, label = "Fast Scan")
                FeatureIcon(icon = Icons.Filled.CheckCircle, label = "Verified")
            }
        }
    }
}


@Composable
fun SecurityBadge() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.White.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "✨",
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SECURE ACCESS SYSTEM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun NFCWaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_waves")

    repeat(3) { index ->
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(index * 600)
            ),
            label = "wave_scale_$index"
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(index * 600)
            ),
            label = "wave_alpha_$index"
        )

        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .alpha(alpha)
                .background(
                    color = Color.Transparent,
                    shape = CircleShape
                )
                .then(
                    Modifier.drawRing(
                        color = Color(0xFF00A86B),
                        strokeWidth = 3.dp
                    )
                )
        )
    }
}

fun Modifier.drawRing(color: Color, strokeWidth: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.drawBehind {
        drawCircle(
            color = color,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
)

@Composable
fun FeatureIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun AnimatedBackgroundBlobs() {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")

    val blob1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1"
    )

    val blob2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.15f)
    ) {
        // Blob 1
        Box(
            modifier = Modifier
                .offset(x = 40.dp, y = (80 + blob1Offset).dp)
                .size(250.dp)
                .blur(60.dp)
                .background(Color.White, CircleShape)
        )

        // Blob 2
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-40).dp, y = (-100 + blob2Offset).dp)
                .size(350.dp)
                .blur(70.dp)
                .background(Color(0xFF00CED1), CircleShape)
        )

        // Blob 3
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(400.dp)
                .blur(80.dp)
                .background(Color(0xFF00FFD1), CircleShape)
        )
    }
}


@Composable
fun FloatingParticles() {
    val particleCount = 20
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(particleCount) { index ->
            val offsetX by infiniteTransition.animateFloat(
                initialValue = (index * 50f) % 300f,
                targetValue = ((index * 50f) % 300f) + 30f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 3000 + (index * 200),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "particle_x_$index"
            )

            val offsetY by infiniteTransition.animateFloat(
                initialValue = (index * 70f) % 600f,
                targetValue = ((index * 70f) % 600f) - 40f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 4000 + (index * 150),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "particle_y_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 2000 + (index * 100),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "particle_alpha_$index"
            )

            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp, y = offsetY.dp)
                    .size((4 + (index % 3) * 2).dp)
                    .alpha(alpha)
                    .background(
                        color = if (index % 3 == 0) Color.White
                        else if (index % 3 == 1) Color(0xFF00FFD1)
                        else Color(0xFF00CED1),
                        shape = CircleShape
                    )
                    .blur((2 + (index % 2)).dp)
            )
        }
    }
}