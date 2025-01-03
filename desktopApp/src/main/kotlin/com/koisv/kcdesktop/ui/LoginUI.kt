package com.koisv.kcdesktop.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.koisv.kcdesktop.Tools
import com.koisv.kcdesktop.Tools.getConnected
import com.koisv.kcdesktop.WSHandler
import com.koisv.kcdesktop.WSHandler.autoLogin
import com.koisv.kcdesktop.WSHandler.loggedInWith
import com.koisv.kcdesktop.WSHandler.sendRegister
import com.koisv.kcdesktop.WSHandler.sessionFailed
import com.koisv.kcdesktop.WSHandler.sessionOpened
import com.koisv.kcdesktop.WSHandler.wsSession
import com.koisv.kcdesktop.config
import com.koisv.kcdesktop.ui.MainUI.AlertSnackBar
import com.koisv.kcdesktop.ui.MainUI.HeaderBar
import com.koisv.kcdesktop.ui.MainUI.InputField
import com.koisv.kcdesktop.ui.MainUI.ProgressAlert
import com.koisv.kcdesktop.ui.MainUI.checkboxPadding
import com.koisv.kcdesktop.ui.MainUI.defaultPadding
import com.koisv.kcdesktop.ui.MainUI.endTextPadding
import com.koisv.kcdesktop.ui.MainUI.idInput
import com.koisv.kcdesktop.ui.MainUI.isRecovery
import com.koisv.kcdesktop.ui.MainUI.isRegister
import com.koisv.kcdesktop.ui.MainUI.keyFileExceed
import com.koisv.kcdesktop.ui.MainUI.loginAlert
import com.koisv.kcdesktop.ui.MainUI.nickInput
import com.koisv.kcdesktop.ui.MainUI.otpInput
import com.koisv.kcdesktop.ui.MainUI.progressText
import com.koisv.kcdesktop.ui.MainUI.progressVisible
import com.koisv.kcdesktop.ui.MainUI.showSnackbar
import com.koisv.kcdesktop.ui.MainUI.smallPadding
import com.koisv.kcdesktop.ui.MainUI.snackbarText
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object LoginUI {

    var buttonFor by mutableStateOf("접속 중...")
    var inputEnabled by mutableStateOf(false)
    var buttonEnabled by mutableStateOf(false)

    var logout by mutableStateOf(false)

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
                title = { Row {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = "Account Alert",
                        modifier = MainUI.iconTopPadding,
                    )
                    Text(title)
                } },
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
                        MainUI.TransparentButton("로그인") {
                            isRegister = false
                            loginAlert = false
                            buttonFor = if (sessionOpened && !sessionFailed) "로그인" else "재접속" }
                        MainUI.TransparentButton("회원가입") {
                            idInput = ""
                            isRegister = true
                            loginAlert = false
                            coroutineScope.launch { otpUIProgress() }
                            buttonFor = if (sessionOpened && !sessionFailed) "회원가입" else "재접속" }
                    }

                },
                shape = RoundedCornerShape(8.dp),
                backgroundColor = MainUI.backgroundColor2,
                contentColor = Color.Black
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    @Preview
    fun Authenticate(nav: NavHostController) {
        val keys = Tools.getKeys()
        val fcMan = LocalFocusManager.current

        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar (
                        contentColor = MainUI.nameColor,
                        backgroundColor = MainUI.backgroundGolor3,
                        elevation = 12.dp
                    ) {
                        Row (verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = Tools.painterResource("icon.webp"),
                                contentDescription = "KoiChat",
                                modifier = Modifier
                                    .padding(7.dp, 0.dp, 12.dp)
                                    .size(32.dp)
                            )
                            HeaderBar("KoiChat Client [WSS]")
                        }
                    }
                }
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
                                        if (!isRegister && idInput.isNotEmpty()) progressText = "로그인 중..."
                                        else if (otpInput.isNotEmpty()) progressText = "가입 요청 중..."
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
                    if (!isRecovery) {
                        InputField(
                            value = idInput,
                            onValueChange = { idInput = it },
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
                    }
                    if (isRegister && !isRecovery) {
                        InputField(
                            value = nickInput,
                            onValueChange = { nickInput = it },
                            label = "닉네임 [선택]",
                            modifier = smallPadding,
                            isEnable = inputEnabled
                        )
                    }
                    if (isRegister || isRecovery) {
                        InputField(
                            value = otpInput,
                            onValueChange = { if ( it.length <= 4 && it.all { it.isDigit() } )
                                otpInput = it },
                            label = "OTP",
                            modifier = smallPadding,
                            isEnable = inputEnabled,
                            isPassword = true,
                            onDone = {
                                if (otpInput.isNotEmpty()) {
                                    fcMan.clearFocus()
                                    progressText = "가입 요청 중..."
                                    progressVisible = true
                                }
                            }
                        )
                    }
                    Row(
                        modifier = smallPadding,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            modifier = checkboxPadding
                                .shadow(4.dp, AbsoluteRoundedCornerShape(8.dp), clip = true)
                                .height(36.dp),
                            onClick = {
                                progressText = when (buttonFor) {
                                    "OTP 다시 요청" -> "OTP 요청 중..."
                                    "재접속" -> "서버 연결 중..."
                                    "가입하기" -> "가입 요청 중..."
                                    "계정 복구" -> "복구 중..."
                                    else -> "로그인 중..."
                                }
                                progressVisible = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = MainUI.nameColor,
                                contentColor = MainUI.backgroundGolor3
                            ),
                            shape = AbsoluteRoundedCornerShape(8.dp),
                            enabled = buttonEnabled
                        ) { Text(buttonFor) }
                        Checkbox(
                            checked = autoLogin,
                            onCheckedChange = { autoLogin = it },
                            enabled = buttonEnabled,
                            modifier = checkboxPadding
                        )
                        Text("자동 로그인", modifier = endTextPadding)
                        if (!isRegister && !isRecovery) TextButton(
                            onClick = {
                                if (idInput.isNotEmpty()) {
                                    buttonFor = "복구 시도 중..."
                                    progressText = "복구 요청 중..."
                                    progressVisible = true
                                } else {
                                    snackbarText = "복구할 아이디를 입력하세요."
                                    showSnackbar = true
                                }
                            },
                            enabled = inputEnabled,
                            colors = ButtonDefaults.buttonColors(
                                contentColor = MainUI.nameColor,
                                backgroundColor = Color.Transparent
                            ),
                            modifier = endTextPadding
                        ) { Text("계정 복구하기") }
                    }
                    ProgressAlert(visible = progressVisible, text = progressText)
                }
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    AlertSnackBar(text = snackbarText, visible = showSnackbar)
                    if (keyFileExceed)
                        AlertSnackBar(text = "키 파일이 너무 많습니다! 5개만 사용됩니다.", visible = keyFileExceed)
                }
            }
        }
        LaunchedEffect(sessionFailed) {
            if (sessionFailed) {
                buttonFor = "재접속"
                inputEnabled = false
                buttonEnabled = true
                snackbarText = "서버 연결이 끊어졌습니다! 연결 상태를 확인하거나 관리자에게 문의하세요."
                showSnackbar = true
                progressVisible = false
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
                val loginResult = keys.map { Pair(it, WSHandler.sendLogin(idInput, it)) }

                WSHandler.logger.debug("Login result: {}", loginResult)

                if (loginResult.any { it.second == 0 } ) {
                    val confingStream = FileOutputStream("config.ini")
                    config.setProperty("autoLogin", autoLogin.toString())
                    config.setProperty("autoLoginId", idInput)
                    config.store(confingStream, "KoiChat Desktop Client Config")
                    confingStream.close()

                    wsSession.send(Frame.Text("wsc:history"))
                    nav.navigate("chat") { launchSingleTop = true }
                }
                snackbarText =
                    when {
                        loginResult.any { it.second == 0 } -> "로그인 완료! 환영합니다, ${loggedInWith?.nickname ?: loggedInWith?.id}님."
                        loginResult.any { it.second == 2 } -> "로그인 실패! 이미 로그인 된 아이디입니다."
                        else -> "로그인 실패! 아이디, 키 파일을 점검하거나 관리자에게 문의 바랍니다."
                    }
                showSnackbar = true
                progressVisible = false
            }

            if (progressVisible) {
                if (logout) {
                    nickInput = ""
                    otpInput = ""
                    buttonFor = "대기 중..."
                    loginAlert = true
                    buttonEnabled = true
                    logout = false
                    progressVisible = false
                } else when (buttonFor) {
                    "가입하기" -> {
                        val result = sendRegister(idInput, if (nickInput.isNotBlank()) nickInput else null, otpInput)
                        if (result == 0.toShort()) {
                            nav.navigate("chat") { launchSingleTop = true }
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
                    "복구 시도 중..." -> {
                        val request = WSHandler.requestRecovery(idInput)

                        if (request) {
                            isRecovery = true
                            buttonFor = "계정 복구"
                            snackbarText = "복구 요청 완료! OTP를 요청하세요."
                            showSnackbar = true
                        } else {
                            snackbarText = "복구 요청 실패! 아이디를 확인하거나 관리자에게 문의하세요."
                            showSnackbar = true
                        }
                        progressVisible = false
                    }
                    "계정 복구" -> {
                        val request = WSHandler.sendRecovery(idInput, otpInput)
                        if (request) {
                            nav.navigate("chat") { launchSingleTop = true }
                            wsSession.send(Frame.Text("wsc:history"))
                            snackbarText =
                                "복구 완료! 다시 만나서 반갑습니다, ${loggedInWith?.nickname ?: loggedInWith?.id}님."
                        } else {
                            otpInput = ""
                            buttonFor = "로그인"
                            snackbarText = "복구 실패! OTP를 확인하거나 관리자에게 문의하세요."
                        }

                        isRecovery = false
                        progressVisible = false
                        showSnackbar = true
                    }
                    "재접속", "접속 중..." -> {
                        progressText = "서버 연결 중..."
                        var connected = getConnected()
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
                                        idInput = WSHandler.autoLoginId
                                        loginNext()
                                    }
                                    showSnackbar = true
                                }
                                isRegister -> otpUIProgress()
                                else -> {
                                    if (buttonFor == "재접속") {
                                        snackbarText = "서버 연결 완료! 로그인 하세요."
                                        showSnackbar = true
                                    }
                                    buttonFor = "로그인"
                                    inputEnabled = true
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
                        otpUIProgress()
                        progressVisible = false
                    }
                    "로그인" -> {
                        progressText = "로그인 중..."
                        loginNext()
                    }
                }
            }
        }
    }
}