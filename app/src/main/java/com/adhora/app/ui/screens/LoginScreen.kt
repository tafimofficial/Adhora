package com.adhora.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhora.app.engine.NativeEngine
import com.adhora.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var password2fa by remember { mutableStateOf("") }
    var passwordHint by remember { mutableStateOf("") }

    // API Credentials State (Default: Telegram Desktop 2040, which avoids mobile Play Integrity CAPTCHA)
    var selectedPreset by remember { mutableStateOf("desktop") } // "desktop", "android", "custom"
    var apiId by remember { mutableStateOf("2040") }
    var apiHash by remember { mutableStateOf("b18441a1ff607e10a989891a5462e627") }
    var showApiSettings by remember { mutableStateOf(false) }

    var step by remember { mutableStateOf(1) } // 1: Phone, 2: OTP
    var show2faDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Logo & Branding
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.5.dp, ElectricLime, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "ADHORA",
                    tint = ElectricLime,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ADHORA",
                color = ElectricLime,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text(
                text = "Telegram Native Fast Media Client",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Compact Error Alert
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2E1315))
                        .border(1.dp, ErrorRed, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = ErrorRed,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFFFB4B4),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Main Input Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
                    },
                    label = "LoginSteps"
                ) { targetStep ->
                    if (targetStep == 1) {
                        // Step 1: Phone Input
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Sign In With Telegram",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Enter your mobile number to receive verification code",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone Number", color = TextSecondary) },
                                placeholder = { Text("+8801700000000", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = "Phone", tint = ElectricLime)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricLime,
                                    unfocusedBorderColor = SurfaceBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = ElectricLime
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (phone.isBlank()) {
                                        errorMessage = "Please enter your Telegram phone number"
                                        return@Button
                                    }
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val idInt = apiId.toIntOrNull() ?: 2040
                                        val res = NativeEngine.requestOtp(idInt, apiHash.trim(), phone.trim())
                                        isLoading = false
                                        if (res.status == "ok") {
                                            step = 2
                                        } else {
                                            val raw = res.message ?: "Failed to send code"
                                            errorMessage = when {
                                                raw.contains("RECAPTCHA_CHECK", ignoreCase = true) ->
                                                    "Telegram Anti-Bot / ReCAPTCHA ট্রিগার করেছে। অনুগ্রহ করে নিচের 'Custom API Credentials' এ আপনার my.telegram.org এর নিজস্ব API ID & Hash বসান অথবা আপনার অফিশিয়াল টেলিগ্রাম অ্যাপটি ওপেন রাখুন।"
                                                raw.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                                                    "ফোন নাম্বারটি সঠিক নয়। দয়া করে কান্ট্রি কোড সহ দিন (+8801...)"
                                                raw.contains("PHONE_NUMBER_FLOOD", ignoreCase = true) ->
                                                    "অনেকবার কোড পাঠানোর চেষ্টা করা হয়েছে (Flood Wait)। কিছু সময় পর চেষ্টা করুন।"
                                                else -> raw
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricLime,
                                    contentColor = PitchBlack,
                                    disabledContainerColor = LimeDim,
                                    disabledContentColor = TextMuted
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = PitchBlack,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Send Login Code",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Step 2: OTP Input
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Verification Code",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Code sent to Telegram app for $phone",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { otpCode = it },
                                label = { Text("5-Digit OTP Code", color = TextSecondary) },
                                leadingIcon = {
                                    Icon(Icons.Default.Security, contentDescription = "OTP", tint = ElectricLime)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricLime,
                                    unfocusedBorderColor = SurfaceBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = ElectricLime
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (otpCode.isBlank()) {
                                        errorMessage = "Please enter the verification code"
                                        return@Button
                                    }
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val res = NativeEngine.verifyOtp(otpCode.trim())
                                        isLoading = false
                                        if (res.status == "ok") {
                                            onLoginSuccess()
                                        } else if (res.status == "2fa_required") {
                                            passwordHint = res.hint ?: ""
                                            show2faDialog = true
                                        } else {
                                            errorMessage = res.message ?: "Invalid code"
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricLime,
                                    contentColor = PitchBlack,
                                    disabledContainerColor = LimeDim,
                                    disabledContentColor = TextMuted
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = PitchBlack,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Verify & Continue",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            TextButton(
                                onClick = {
                                    step = 1
                                    otpCode = ""
                                    errorMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Change Phone Number", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Expandable API Settings Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                    .clickable { showApiSettings = !showApiSettings }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "API Settings",
                            tint = ElectricLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Telegram API Configuration",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selectedPreset == "desktop") "Preset: Telegram Desktop (No Captcha)"
                                else if (selectedPreset == "android") "Preset: Telegram Android"
                                else "Custom (my.telegram.org)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = if (showApiSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = TextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = showApiSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Choose API Preset or Enter Custom",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedPreset == "desktop",
                            onClick = {
                                selectedPreset = "desktop"
                                apiId = "2040"
                                apiHash = "b18441a1ff607e10a989891a5462e627"
                            },
                            label = { Text("Desktop", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricLime,
                                selectedLabelColor = PitchBlack,
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary
                            )
                        )
                        FilterChip(
                            selected = selectedPreset == "android",
                            onClick = {
                                selectedPreset = "android"
                                apiId = "6"
                                apiHash = "eb06d4abfb49dc3eeb1aeb98ae0f581e"
                            },
                            label = { Text("Android", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricLime,
                                selectedLabelColor = PitchBlack,
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary
                            )
                        )
                        FilterChip(
                            selected = selectedPreset == "custom",
                            onClick = {
                                selectedPreset = "custom"
                            },
                            label = { Text("Custom", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricLime,
                                selectedLabelColor = PitchBlack,
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = apiId,
                        onValueChange = {
                            apiId = it
                            selectedPreset = "custom"
                        },
                        label = { Text("API ID", color = TextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricLime,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiHash,
                        onValueChange = {
                            apiHash = it
                            selectedPreset = "custom"
                        },
                        label = { Text("API Hash", color = TextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricLime,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Get your personal API ID & Hash from my.telegram.org",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // 2FA Password Popup Dialog
        if (show2faDialog) {
            AlertDialog(
                onDismissRequest = { show2faDialog = false },
                containerColor = SurfaceDark,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = "2FA", tint = ElectricLime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2-Step Verification", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "This Telegram account is protected by a 2FA password.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        if (passwordHint.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Hint: $passwordHint",
                                color = ElectricLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password2fa,
                            onValueChange = { password2fa = it },
                            label = { Text("2FA Password", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricLime,
                                unfocusedBorderColor = SurfaceBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (password2fa.isBlank()) return@Button
                            isLoading = true
                            scope.launch {
                                val res = NativeEngine.verify2fa(password2fa.trim())
                                isLoading = false
                                if (res.status == "ok") {
                                    show2faDialog = false
                                    onLoginSuccess()
                                } else {
                                    errorMessage = res.message ?: "Incorrect 2FA password"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = PitchBlack)
                    ) {
                        Text("Confirm Password", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { show2faDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}
