package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onStartAssessment: () -> Unit,
    onEnterClinicianPortal: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var headerVisible by remember { mutableStateOf(false) }
    var ctaVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
        delay(200)
        visible = true
        delay(300)
        ctaVisible = true
    }

    // Animated glow pulse for the accent elements
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SlateBg, SlateBgElevated, Color(0xFF0D1829))
                )
            )
            .drawBehind {
                // Subtle ambient glow orbs for depth
                drawCircle(
                    color = ClinicalTeal.copy(alpha = glowAlpha * 0.3f),
                    radius = 280.dp.toPx(),
                    center = Offset(size.width * 0.8f, size.height * 0.15f)
                )
                drawCircle(
                    color = MintSecondary.copy(alpha = glowAlpha * 0.15f),
                    radius = 200.dp.toPx(),
                    center = Offset(size.width * 0.1f, size.height * 0.7f)
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Content
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    // Glowing logo circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .drawBehind {
                                drawCircle(
                                    color = ClinicalTeal.copy(alpha = glowAlpha),
                                    radius = 44.dp.toPx()
                                )
                            }
                            .clip(CircleShape)
                            .background(ClinicalTealSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = "Joints.AI Logo",
                            tint = ClinicalTealLight,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Joints.AI",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextTitle,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Personalized Physiotherapy Programs",
                        style = MaterialTheme.typography.titleMedium,
                        color = ClinicalTeal,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Powered by AI · Verified by Clinicians",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Animated Workflow Card
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 100)) +
                        slideInVertically(tween(500, delayMillis = 100)) { 60 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "How It Works",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextTitle,
                                fontWeight = FontWeight.Bold
                            )

                            WorkflowItem(
                                icon = Icons.Default.Assignment,
                                title = "1. Smart Assessment",
                                description = "Select your pain area, log intensity, and complete a digital range-of-motion scan with our AI vision analyzer.",
                                accentColor = ClinicalTeal
                            )

                            WorkflowItem(
                                icon = Icons.Default.Healing,
                                title = "2. AI-Prescribed Program",
                                description = "Our clinical PT model generates evidence-based recovery exercises specifically tailored to your joint metrics.",
                                accentColor = MintSecondary
                            )

                            WorkflowItem(
                                icon = Icons.Default.VerifiedUser,
                                title = "3. Clinician Verification",
                                description = "Every plan is reviewed, modified, and signed off by certified orthopedic physical therapists before activation.",
                                accentColor = GreenSuccess
                            )
                        }
                    }
                }
            }

            // CTAs at bottom
            AnimatedVisibility(
                visible = ctaVisible,
                enter = fadeIn(tween(400, delayMillis = 200)) +
                        slideInVertically(tween(400, delayMillis = 200)) { 40 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Primary CTA with gradient
                    Button(
                        onClick = onStartAssessment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_assessment_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = TextOnAccent
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ClinicalTeal, ClinicalTealLight)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start Free Assessment",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOnAccent
                            )
                        }
                    }

                    TextButton(
                        onClick = onEnterClinicianPortal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clinician_portal_button")
                    ) {
                        Text(
                            text = "Access Clinician Portal",
                            color = TextBody,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkflowItem(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color = ClinicalTeal
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTitle,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextBody,
                lineHeight = 18.sp
            )
        }
    }
}
