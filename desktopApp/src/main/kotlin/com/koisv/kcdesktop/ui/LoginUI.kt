package com.koisv.kcdesktop.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.koisv.kcdesktop.WSHandler
import com.koisv.kcdesktop.WSHandler.autoLogin
import com.koisv.kcdesktop.WSHandler.sendRegister
import com.koisv.kcdesktop.WSHandler.sessionOpened
import com.koisv.kcdesktop.config
import com.koisv.kcdesktop.ui.MainUI.HeaderBar
import com.koisv.kcdesktop.ui.MainUI.InputField
import com.koisv.kcdesktop.ui.MainUI.ProgressAlert
import com.koisv.kcdesktop.ui.MainUI.SlideInSnackbar
import com.koisv.kcdesktop.ui.MainUI.checkboxPadding
import com.koisv.kcdesktop.ui.MainUI.defaultPadding
import com.koisv.kcdesktop.ui.MainUI.endTextPadding
import com.koisv.kcdesktop.ui.MainUI.id
import com.koisv.kcdesktop.ui.MainUI.isRegister
import com.koisv.kcdesktop.ui.MainUI.keyFileExceed
import com.koisv.kcdesktop.ui.MainUI.loginAlert
import com.koisv.kcdesktop.ui.MainUI.nickname
import com.koisv.kcdesktop.ui.MainUI.otp
import com.koisv.kcdesktop.ui.MainUI.progressText
import com.koisv.kcdesktop.ui.MainUI.progressVisible
import com.koisv.kcdesktop.ui.MainUI.showSnackbar
import com.koisv.kcdesktop.ui.MainUI.smallPadding
import com.koisv.kcdesktop.ui.MainUI.snackbarText
import com.koisv.kcdesktop.ui.MainUI.transparentColor
import com.koisv.kcdesktop.ui.Tools.getConnected
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object LoginUI {

    var buttonFor by mutableStateOf("접속 중...")
    var inputEnabled by mutableStateOf(false)

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun otpUIProgress() {
        progressText = "OTP 요청 중..."
        if (getConnected() && WSHandler.otpRequest()) {
            snackbarText = "OTP 요청 완료! 관리자에게 OTP를 요청하세요."
            buttonFor = "가입하기"
            inputEnabled = true
        } else {
            snackbarText = "OTP 요청 실패! 관리자에게 문의하거나 연결을 확인하세요."
            buttonFor = "OTP 다시 요청"
        }
        showSnackbar = true
        progressVisible = false
    }

    @Composable
    fun LoginAlert(visible: Boolean, title: String, text: String) {
        val coroutineScope = rememberCoroutineScope()
        if (visible) {
            AlertDialog(
                onDismissRequest = { /* Do nothing */ },
                title = { Text(title) },
                text = { Text(text) },
                buttons = {
                    Row (
                        modifier = smallPadding
                            .fillMaxWidth()
                            .height(36.dp)
                        ,
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = {
                                isRegister = false
                                loginAlert = false
                                buttonFor = if (sessionOpened) "로그인" else "재접속"
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = transparentColor)
                        ) { Text("로그인") }
                        Button(
                            onClick = {
                                isRegister = true
                                loginAlert = false
                                coroutineScope.launch { otpUIProgress() }
                                buttonFor = if (sessionOpened) "회원가입" else "재접속"
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = transparentColor)
                        ) { Text("회원가입") }
                    }

                },
                shape = RoundedCornerShape(8.dp),
                backgroundColor = Color(100, 230, 100, 220),
                contentColor = Color.Black
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    @Preview
    fun Authenticate(keys: List<File>, nav: NavHostController) {
        val coroutineScope = rememberCoroutineScope()
        var buttonEnabled by remember { mutableStateOf(false) }

        val fcMan = LocalFocusManager.current

        MaterialTheme {
            Scaffold(
                topBar = { HeaderBar("KoiChat Client [WSS]") }
            ) {
                LoginAlert(
                    visible = loginAlert,
                    title = "기존 계정 감지",
                    text = "회원가입 또는 로그인을 선택하세요."
                )
                Column(
                    modifier = defaultPadding
                        .fillMaxSize().focusable()
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown)
                                when (it.key) {
                                    Key.DirectionDown -> {
                                        fcMan.moveFocus(FocusDirection.Down)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        fcMan.moveFocus(FocusDirection.Up)
                                        true
                                    }
                                    Key.Enter -> {
                                        if (!isRegister && id.isNotEmpty()) progressText = "로그인 중..."
                                        else if (otp.isNotEmpty()) progressText = "가입 요청 중..."
                                        fcMan.clearFocus()
                                        progressVisible = true
                                        true
                                    }
                                    else -> false
                                } else false
                        },
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    InputField(
                        value = id,
                        onValueChange = { id = it },
                        label = "아이디",
                        modifier = smallPadding,
                        isEnable = inputEnabled,
                        onDone = {
                            if (!isRegister) {
                                fcMan.clearFocus()
                                progressText = "로그인 중..."
                                progressVisible = true
                            }
                        }
                    )
                    if (isRegister) {
                        InputField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = "닉네임 [선택]",
                            modifier = smallPadding,
                            isEnable = inputEnabled
                        )
                        InputField(
                            value = otp,
                            onValueChange = { if ( it.length <= 4 && it.all { it.isDigit() } )
                                otp = it },
                            label = "OTP",
                            modifier = smallPadding,
                            isEnable = inputEnabled,
                            isPassword = true,
                            onDone = {
                                if (otp.isNotEmpty()) {
                                    fcMan.clearFocus()
                                    progressText = "가입 요청 중..."
                                    progressVisible = true
                                }
                            }
                        )
                    }
                    Row(modifier = smallPadding) {
                        Button(
                            onClick = {
                                progressText = when (buttonFor) {
                                    "OTP 다시 요청" -> "OTP 요청 중..."
                                    "재접속" -> "서버 연결 중..."
                                    "가입하기" -> "가입 요청 중..."
                                    else -> "로그인 중..."
                                }
                                progressVisible = true
                            },
                            enabled = buttonEnabled
                        ) { Text(buttonFor) }
                        Checkbox(
                            checked = autoLogin,
                            onCheckedChange = { autoLogin = it },
                            enabled = buttonEnabled,
                            modifier = checkboxPadding
                        )
                        Text("자동 로그인", modifier = endTextPadding)
                    }
                    ProgressAlert(visible = progressVisible, text = progressText)
                }
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    SlideInSnackbar(text = snackbarText, visible = showSnackbar)
                    if (keyFileExceed)
                        SlideInSnackbar(text = "키 파일이 너무 많습니다! 5개만 사용됩니다.", visible = keyFileExceed)
                }
            }
        }

        LaunchedEffect(keyFileExceed) {
            if (keyFileExceed) {
                delay(3000)
                keyFileExceed = false
            }
        }

        LaunchedEffect(showSnackbar) {
            if (showSnackbar) {
                delay(3000)
                showSnackbar = false
            }
        }

        LaunchedEffect(progressVisible) {
            suspend fun loginNext() {
                val loginResult = keys.map { Pair(it, WSHandler.sendLogin(id, it)) }

                if (loginResult.any { it.second == 0 } ) {
                    val confingStream = FileOutputStream("config.ini")
                    config.setProperty("autoLogin", autoLogin.toString())
                    config.setProperty("autoLoginId", id)
                    config.store(confingStream, "KoiChat Desktop Client Config")
                    confingStream.close()

                    nav.navigate("chat") {
                        launchSingleTop = true
                        popUpTo("login") { inclusive = true }
                    }
                }
                snackbarText =
                    when {
                        loginResult.any { it.second == 0 } -> "로그인 완료! 환영합니다, ${WSHandler.loggedInWith.nickname ?: WSHandler.loggedInWith.id}님."
                        loginResult.any { it.second == 2 } -> "로그인 실패! 이미 로그인 된 아이디입니다."
                        else -> "로그인 실패! 아이디, 키 파일을 점검하거나 관리자에게 문의 바랍니다."
                    }
                if (!inputEnabled) inputEnabled = true
                if (buttonFor == "접속 중...") buttonFor = "로그인"
                showSnackbar = true
                progressVisible = false
            }

            if (progressVisible) {
                when (buttonFor) {
                    "가입하기" -> {
                        val result = sendRegister(id, if (nickname.isNotBlank()) nickname else null, otp)
                        if (result == 0.toShort()) {
                            nav.navigate("chat") {
                                launchSingleTop = true
                                popUpTo("login") { inclusive = true }
                            }
                        }
                        when (result) {
                            99.toShort() -> snackbarText = "서버 연결 실패! 관리자에게 문의하거나 연결을 확인하세요."
                            0.toShort() -> snackbarText = "가입 완료!"
                            1.toShort() -> snackbarText = "가입 실패! OTP를 확인하거나 다시 시도하세요."
                            2.toShort() -> snackbarText = "가입 실패! 관리자에게 문의하거나 다시 시도하세요."
                            3.toShort() -> snackbarText = "가입 실패! 이미 가입된 아이디입니다."
                            4.toShort() -> snackbarText = "가입 실패! 사용할 수 없는 닉네임입니다."
                        }
                        showSnackbar = true
                        progressVisible = false
                    }
                    "재접속", "접속 중..." -> {
                        progressText = "서버 연결 중..."
                        var connected = coroutineScope.async { getConnected() }.await()
                        if (connected) {
                            when {
                                autoLogin -> {
                                    if (WSHandler.autoLoginId.isEmpty()) {
                                        snackbarText = "자동 로그인 실패! 저장된 아이디가 없습니다."
                                        buttonFor = "로그인"
                                        inputEnabled = true
                                        progressVisible = false
                                    }
                                    else {
                                        loginAlert = false
                                        id = WSHandler.autoLoginId
                                        coroutineScope.launch { loginNext() }
                                    }
                                    showSnackbar = true
                                }
                                isRegister -> otpUIProgress()
                                else -> {
                                    buttonFor = "로그인"
                                    inputEnabled = true
                                    snackbarText = "서버 연결 완료! 로그인 하세요."
                                    showSnackbar = true
                                    progressVisible = false
                                }
                            }
                        } else {
                            snackbarText = "서버 연결 실패! 관리자에게 문의하거나 연결을 확인하세요."
                            buttonFor = "재접속"
                            showSnackbar = true
                        }
                        buttonEnabled = true
                        progressVisible = false
                    }
                    "OTP 다시 요청" -> {
                        coroutineScope.launch { otpUIProgress() }
                        progressVisible = false
                    }
                    "로그인" -> {
                        progressText = "로그인 중..."
                        coroutineScope.launch { loginNext() }
                    }
                }
            }
        }
    }
}