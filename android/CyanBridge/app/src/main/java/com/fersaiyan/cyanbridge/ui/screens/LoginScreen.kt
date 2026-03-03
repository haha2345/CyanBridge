package com.fersaiyan.cyanbridge.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.auth.LoginMethod
import com.fersaiyan.cyanbridge.auth.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit = {}, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val userRepo = remember { UserRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var selectedMethod by remember { mutableStateOf(LoginMethod.PHONE) }
    var inputValue by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    // Countdown timer for verification code resend
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        // ── Top Bar ──
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }

        Spacer(Modifier.height(16.dp))

        Text(
                "欢迎使用 CyanBridge",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
                "登录后享受完整功能与会员权益",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        // ── Login Method Tabs ──
        val methods = listOf(LoginMethod.PHONE, LoginMethod.EMAIL, LoginMethod.WECHAT)
        val methodLabels = listOf("手机号", "邮箱", "微信")
        val methodIcons = listOf(Icons.Filled.Phone, Icons.Filled.Email, Icons.Filled.Chat)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            methods.forEachIndexed { index, method ->
                SegmentedButton(
                        selected = selectedMethod == method,
                        onClick = {
                            selectedMethod = method
                            codeSent = false
                            inputValue = ""
                            verificationCode = ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, methods.size),
                        icon = { Icon(methodIcons[index], null, Modifier.size(18.dp)) },
                ) { Text(methodLabels[index]) }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (selectedMethod == LoginMethod.WECHAT) {
            // ── WeChat Login Button ──
            WeChatLoginSection(
                    isLoading = isLoading,
                    onLogin = {
                        isLoading = true
                        scope.launch {
                            delay(800) // Simulate network
                            userRepo.loginWithWeChat()
                            isLoading = false
                            Toast.makeText(context, "微信登录成功！", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        }
                    }
            )
        } else {
            // ── Phone / Email Flow ──
            val label = if (selectedMethod == LoginMethod.PHONE) "手机号" else "邮箱"
            val keyboardType =
                    if (selectedMethod == LoginMethod.PHONE) KeyboardType.Phone
                    else KeyboardType.Email

            OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(
                                if (selectedMethod == LoginMethod.PHONE) Icons.Filled.Phone
                                else Icons.Filled.Email,
                                null
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Verification code row
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { if (it.length <= 6) verificationCode = it },
                        label = { Text("验证码") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                        onClick = {
                            if (inputValue.isNotBlank()) {
                                userRepo.sendVerificationCode(inputValue, selectedMethod)
                                codeSent = true
                                countdown = 60
                                Toast.makeText(context, "验证码已发送（模拟：888888）", Toast.LENGTH_LONG)
                                        .show()
                            }
                        },
                        enabled = inputValue.isNotBlank() && countdown == 0,
                        modifier = Modifier.height(56.dp),
                ) { Text(if (countdown > 0) "${countdown}s" else "发送") }
            }

            AnimatedVisibility(visible = codeSent) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                            "💡 模拟验证码：888888",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Login button
            Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            delay(500) // Simulate
                            val user =
                                    userRepo.verifyAndLogin(
                                            inputValue,
                                            verificationCode,
                                            selectedMethod
                                    )
                            isLoading = false
                            if (user != null) {
                                Toast.makeText(
                                                context,
                                                "登录成功！欢迎 ${user.nickname}",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                                onLoginSuccess()
                            } else {
                                Toast.makeText(context, "验证码错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = inputValue.isNotBlank() && verificationCode.length == 6 && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("登录 / 注册", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Footer ──
        Text(
                "登录即表示同意《用户协议》和《隐私政策》",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun WeChatLoginSection(isLoading: Boolean, onLogin: () -> Unit) {
    Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Icon(
                Icons.Filled.Chat,
                "微信",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
                "点击下方按钮使用微信授权登录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Button(
                onClick = onLogin,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                        ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Filled.Chat, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("微信一键登录", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        Text(
                "（模拟登录，无需真实微信授权）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
