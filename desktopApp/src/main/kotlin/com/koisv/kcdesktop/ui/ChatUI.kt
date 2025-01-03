package com.koisv.kcdesktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ForwardToInbox
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.RecentActors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.koisv.kcdesktop.*
import com.koisv.kcdesktop.Command.isValidCommand
import com.koisv.kcdesktop.Tools.chatTimeFormat
import com.koisv.kcdesktop.WSHandler.loggedIn
import com.koisv.kcdesktop.WSHandler.loggedInWith
import com.koisv.kcdesktop.WSHandler.messages
import com.koisv.kcdesktop.WSHandler.onlines
import com.koisv.kcdesktop.ui.MainUI.AlertSnackBar
import com.koisv.kcdesktop.ui.MainUI.CancelableSnackBar
import com.koisv.kcdesktop.ui.MainUI.ConfirmDialog
import com.koisv.kcdesktop.ui.MainUI.InputDialog
import com.koisv.kcdesktop.ui.MainUI.UserCtxMenu
import com.koisv.kcdesktop.ui.MainUI.logoutDialog
import com.koisv.kcdesktop.ui.MainUI.message
import com.koisv.kcdesktop.ui.MainUI.progressText
import com.koisv.kcdesktop.ui.MainUI.progressVisible
import com.koisv.kcdesktop.ui.MainUI.showSnackbar
import com.koisv.kcdesktop.ui.MainUI.showUsers
import com.koisv.kcdesktop.ui.MainUI.snackbarText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object ChatUI {
    private val animationSpecFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = 0.1f
    )

    var historyLoading = true
    var commandHelper by mutableStateOf(false)
    var commandHelpList by mutableStateOf("")
    var prvMsgTarget by mutableStateOf<WSHandler.WSCUser?>(null)

    fun heightCalculate(current: Dp, target: String): Dp =
        if (current >= 148.dp && target.lines().size > 6) 148.dp
        else 48.dp + (((target.lines().size - 1) * 20).dp)


    fun getRelativeTime(lastOnline: LocalDateTime): String {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val duration = Duration.between(lastOnline, now)

        return when {
            duration.toMinutes() < 1 -> "방금 전"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}분 전"
            duration.toHours() < 24 -> "${duration.toHours()}시간 전"
            duration.toDays() < 7 -> "${duration.toDays()}일 전"
            duration.toDays() < 30 -> "${duration.toDays() / 7}주 전"
            duration.toDays() < 365 -> "${duration.toDays() / 30}달 전"
            else -> "굉장히 오래 전"
        } + "에 접속 함${if (duration.toDays() >= 365) " [와우!]" else ""}"
    }

    @Composable
    fun userListItem(visible: Boolean) {
        val userScrollState = rememberScrollState()
        val userLSize: Float by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = animationSpecFloat,
            label = "size_ul"
        )
        Box(
            modifier = Modifier
                .background(MainUI.backgroundColor2)
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
            propagateMinConstraints = true,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .verticalScroll(userScrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (user in onlines) {
                    var showContextMenu by remember { mutableStateOf(false) }
                    val onlineTime = mutableStateOf(getRelativeTime(user.lastOnline))
                    Row (
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showContextMenu = !showContextMenu }
                    ) {
                        if (user.onMobile) Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = "모바일",
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .fillMaxHeight()
                        ) else Icon(
                            Icons.Default.Computer,
                            contentDescription = "PC",
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .fillMaxHeight()
                        )
                        Column (
                            modifier = Modifier
                                .padding(start = 4.dp)
                        ) {
                            Text(
                                if (user.nickname == null) user.id else "${user.nickname} [${user.id}]",
                                modifier = Modifier.padding(start = 6.dp),
                                fontSize = (16 * userLSize).sp
                            )
                            Text(
                                onlineTime.value,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                                fontSize = (10 * userLSize).sp
                            )
                            user.UserCtxMenu(showContextMenu) { showContextMenu = false; showUsers = false }
                        }
                    }
                    Divider(color = MainUI.nameColor, thickness = 1.dp)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Preview
    @Composable
    fun ChatScreen(nav: NavHostController) {
        var connectionFailMsg by remember { mutableStateOf("연결이 끊어졌습니다...") }
        var changeNick by remember { mutableStateOf<String?>(null) }
        var messageSend by remember { mutableStateOf(false) }
        var returnToLogin by remember { mutableStateOf(false) }
        var isLogout by remember { mutableStateOf(false) }
        val msgScrollState = rememberScrollState()
        val coroutine = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        Scaffold(
            topBar = {
                TopAppBar(
                    backgroundColor = MainUI.backgroundColor1,
                    title = {
                        Row {
                            if (!MainUI.disconnectedUI) {
                                Icon(
                                    Icons.Default.Public, contentDescription = "온라인",
                                    modifier = MainUI.endTextPadding
                                ); Text("온라인")
                            } else {
                                Icon(
                                    Icons.Default.PublicOff, contentDescription = "연결 끊김",
                                    modifier = MainUI.endTextPadding
                                ); Text("연결 끊김")
                            }
                            Text(" | ")
                            if (loggedIn) Icon(
                                Icons.Default.PersonOutline, contentDescription = "사용자",
                                modifier = MainUI.endTextPadding
                            ) else Icon(
                                Icons.Outlined.PersonOff, contentDescription = "로그아웃 됨",
                                modifier = MainUI.endTextPadding
                            )
                            Text((loggedInWith?.nickname ?: loggedInWith?.id) ?: "(로그아웃)")
                            Text(" | ")
                            Icon(
                                Icons.Outlined.Group, contentDescription = "접속자",
                                modifier = MainUI.endTextPadding
                            )
                            Text("${onlines.size}명 접속 중")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showUsers = !showUsers }) {
                            Icon(Icons.Outlined.RecentActors, contentDescription = "접속자 목록")
                        }
                    }
                )
            }
        ) {
            InputDialog(
                open = MainUI.nickDialog,
                title = "닉네임 변경",
                text = "변경할 닉네임을 입력하세요.",
                onClose = { MainUI.nickDialog = false },
                onDone = { changeNick = it }
            )

            /**
             * 로그아웃 확인 다이얼로그
             * @TODO 로그아웃 후 다른 아이디 로그인 이상 없게 하기
             */
            ConfirmDialog(
                icon = Icons.AutoMirrored.Default.Logout,
                title = "로그아웃",
                text = "정말 로그아웃 하시겠습니까?",
                open = logoutDialog,
                onClose = { logoutDialog = false },
                onConfirm = {
                    config.setProperty("autoLogin", "false")
                    config.setProperty("autoLoginId", "")
                    config.store(java.io.FileWriter(configFileName), "KoiChat Desktop Client Config")
                    snackbarText = "개발 중인 기능입니다. 자동 로그인이 비활성화 되었으므로 프로그램을 종료하여 로그아웃 해주세요."
                    showSnackbar = true
                    //isLogout = true; returnToLogin = true
                }
            )
            MainUI.ProgressAlert(MainUI.disconnectedUI, connectionFailMsg)
            MainUI.ProgressAlert(progressVisible, progressText)

            Row(modifier = Modifier.fillMaxSize()) {
                val userLSize: Float by animateFloatAsState(
                    targetValue = if (showUsers) 0.2f else 0f,
                    animationSpec = animationSpecFloat,
                    label = "size_ul"
                )
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.Bottom)
                        .fillMaxWidth(1 - userLSize)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomStart
                ) {
                    var height by remember { mutableStateOf(48.dp) }
                    Column(
                        modifier = Modifier
                            .padding(start = 1.dp, bottom = height + 16.dp)
                            .verticalScroll(msgScrollState),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (i in messages) {
                            Divider(color = Color.LightGray, thickness = 1.dp)
                            if (i.first == WSHandler.SERVER_MESSAGE_ID)
                                Text(
                                    i.third.first,
                                    fontSize = 12.sp,
                                    color = MainUI.serverMessageColor,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            else Card(
                                shape = RoundedCornerShape(4.dp)
                            ) { Column {
                                Row (
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val recipient = i.third.second
                                    Row (verticalAlignment = Alignment.CenterVertically) {
                                        if (recipient != null) {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.ForwardToInbox,
                                                contentDescription = "귓속말",
                                                modifier = MainUI.iconPadding.size(16.dp),
                                                tint = MainUI.recipientColor
                                            )
                                            Text(i.first, fontSize = 12.sp, color = MainUI.nameColor)
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "보냄",
                                                modifier = MainUI.iconShortPadding.size(16.dp)
                                            )
                                            Text(recipient, fontSize = 12.sp, color = MainUI.recipientColor)
                                        }
                                        else {
                                            Icon(
                                                Icons.Default.ChatBubble,
                                                contentDescription = "메시지",
                                                modifier = MainUI.iconPadding.size(16.dp),
                                                tint = MainUI.nameColor
                                            )
                                            Text(i.first, fontSize = 12.sp, color = MainUI.nameColor) }
                                    }
                                    Text(
                                        i.second.format(chatTimeFormat),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                                Text(i.third.first, modifier = Modifier.padding(start = 20.dp))
                            } }
                            if (!historyLoading) coroutine.launch {
                                msgScrollState.animateScrollTo(
                                    msgScrollState.maxValue,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                        AlertSnackBar(text = snackbarText, visible = showSnackbar)
                        AlertSnackBar(text = commandHelpList, visible = commandHelper)
                        CancelableSnackBar(
                            text =
                                if (prvMsgTarget?.nickname != null)
                                    "${prvMsgTarget?.nickname} [${prvMsgTarget?.id}] 님에게 메시지 전송 중..."
                                else "${prvMsgTarget?.id ?: "어딘가의 누군가"} 님에게 메시지 전송 중...",
                            visible = prvMsgTarget != null
                        ) { prvMsgTarget = null }
                    }

                    Divider(
                        modifier = Modifier.padding(bottom = height + 8.dp),
                        color = Color.DarkGray,
                        thickness = 2.25.dp
                    )

                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(height)
                        ,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TextField(
                            value = message,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = MainUI.backgroundColor2,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedLabelColor = MainUI.focusColor,
                                unfocusedLabelColor = MainUI.unfocusColor,
                            ),
                            onValueChange = {
                                message = it
                                if (it.text.startsWith("/")) {
                                    val command = it.text.split(" ")[0]
                                    val usage = Command.getCommandUsage(command)
                                    if (usage != null) {
                                        commandHelpList = usage
                                        commandHelper = true
                                    } else commandHelper = false
                                } else commandHelper = false
                                height = heightCalculate(height, it.text)
                            },
                            label = { Text("메시지 입력") },
                            modifier = Modifier
                                .fillMaxWidth(0.92f - (userLSize * 0.09f))
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        when (it.key) {
                                            Key.Escape -> {
                                                if (prvMsgTarget != null) prvMsgTarget = null
                                                else focusManager.clearFocus()
                                                true
                                            }
                                            Key.Backspace -> {
                                                if (message.text.isBlank() && prvMsgTarget != null)
                                                    prvMsgTarget = null
                                                false
                                            }
                                            Key.Enter -> {
                                                if (it.isShiftPressed) {
                                                    message = message.copy(text = message.text + "\n")
                                                    message = message.copy(selection = TextRange(message.text.length))
                                                    height = heightCalculate(height, message.text)
                                                } else messageSend = true
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            keyboardActions = KeyboardActions(onDone = { messageSend = true })
                        )
                        Button(
                            colors = ButtonDefaults.buttonColors(backgroundColor = MainUI.unfocusColor),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .height(48.dp)
                                .fillMaxWidth(),
                            onClick = { messageSend = true },
                            shape = RoundedCornerShape(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
                        }
                    }
                }
                userListItem(showUsers)
            }

            LaunchedEffect(changeNick) {
                /**
                 * 닉네임 변경 시 닉네임 변경 요청을 보냅니다.
                 */
                if (changeNick != null) {
                    MainUI.nickDialog = false
                    val result = WSHandler.sendNickChange(changeNick)
                    snackbarText =
                        if (result == 0) "닉네임이 변경되었습니다."
                        else "닉네임 변경에 실패했습니다. 재시도 해주세요."
                    showSnackbar = true
                    changeNick = null
                }
            }

            LaunchedEffect(returnToLogin) {
                /**
                 * 로그아웃 시 로그아웃 요청을 보내고 로그인 화면으로 돌아갑니다.
                 */
                if (returnToLogin) {
                    LoginUI.logout = true
                    messages.clear()
                    if (isLogout) {
                        logoutDialog = false
                        progressText = "로그아웃 중..."
                    }
                    progressVisible = true
                    if (isLogout) WSHandler.sendLogout()
                    loggedInWith = null
                    onlines.clear()
                    nav.navigate(Nav.LOGIN.name) { launchSingleTop = true }
                    if (isLogout) {
                        snackbarText = "로그아웃 되었습니다."
                        showSnackbar = true
                        isLogout = false
                    }
                    returnToLogin = false
                }
            }

            LaunchedEffect(messageSend) {
                /**
                 * 메시지 전송 시 메시지를 전송하고 메시지 입력창을 초기화합니다.
                 */
                if (messageSend) {
                    if (
                        message.text.startsWith("/") &&
                        message.text.split(" ")[0].isValidCommand
                    ) {
                        // 명령어 감지
                        if (prvMsgTarget != null) {
                            snackbarText = "귓속말 중 명령어는 사용할 수 없습니다."
                            showSnackbar = true
                        } else {
                            val command = message.text.split(" ")[0]
                            when (command) {
                                "/logout" -> logoutDialog = true
                                "/w", "/msg" -> {
                                    val target = message.text.split(" ").drop(1).joinToString(" ")
                                    val targetUser = onlines.firstOrNull { it.id == target && it.nickname == target }
                                    if (targetUser != null && loggedInWith == targetUser) {
                                        snackbarText = "자기 자신에게 귓속말을 보낼 수 없습니다."
                                        showSnackbar = true
                                    }
                                    else if (targetUser != null) prvMsgTarget = targetUser
                                    else {
                                        snackbarText = "해당 사용자를 찾을 수 없습니다."
                                        showSnackbar = true
                                    }
                                }
                                "/r" -> {
                                    val lastPrvMsg = messages.lastOrNull {
                                        it.third.second == (loggedInWith?.nickname ?: loggedInWith?.id)
                                    }
                                    if (lastPrvMsg != null) {
                                        val target = onlines.firstOrNull {
                                            it.id == lastPrvMsg.first || it.nickname == lastPrvMsg.first
                                        }
                                        if (target != null) prvMsgTarget = target
                                        else {
                                            snackbarText = "사용자를 찾을 수 없거나 로그아웃 상태입니다."
                                            showSnackbar = true
                                        }
                                    } else {
                                        snackbarText = "마지막으로 귓속말을 보낸 사용자가 없습니다."
                                        showSnackbar = true
                                    }
                                }
                                "/nick" -> {
                                    val newNick = message.text.split(" ").drop(1).joinToString(" ")
                                    snackbarText = if (newNick.isNotBlank()) {
                                        val result = WSHandler.sendNickChange(newNick)
                                        when (result) {
                                            0 -> "닉네임이 변경되었습니다."
                                            5 -> "사용할 수 없는 닉네임입니다. 다른 닉네임을 사용해 주세요."
                                            else -> "닉네임 변경에 실패했습니다. 재시도 해주세요."
                                        }
                                    } else {
                                        val result = WSHandler.sendNickChange(null)
                                        when (result) {
                                            0 -> "닉네임이 초기화되었습니다."
                                            else -> "닉네임 초기화에 실패했습니다. 재시도 해주세요."
                                        }
                                    }
                                    loggedInWith = onlines.first { it.id == loggedInWith?.id && !it.onMobile }
                                    showSnackbar = true
                                }
                            }
                        }
                    } else if (message.text.isNotBlank()) {
                        // 일반 메시지 전송
                        WSHandler.sendMessage(message.text, prvMsgTarget?.id)
                        prvMsgTarget = null
                    } else {
                        // 빈 메시지 전송
                        snackbarText = "빈 메시지는 보낼 수 없습니다!"
                        showSnackbar = true
                    }
                    message = message.copy(text = "")
                    commandHelper = false
                    commandHelpList = ""
                }
                messageSend = false
            }

            LaunchedEffect(commandHelpList) {
                /**
                 * 명령어 입력 시 명령어 도움말을 표시합니다.
                 */
                if (commandHelpList.isBlank()) commandHelper = false
            }

            LaunchedEffect(showSnackbar) {
                /**
                 * (스낵바 표시용) 3초 후 false로 변경하여 스낵바를 숨깁니다.
                 */
                if (showSnackbar) {
                    delay(3000)
                    showSnackbar = false
                }
            }

            LaunchedEffect(MainUI.disconnectedUI) {
                /**
                 * 연결이 끊어졌을 때 재접속을 시도합니다.
                 * @TODO 로그인 창에서 발생 시 로그인 오류 고치기
                 */
                if (MainUI.disconnectedUI) {
                    val maxReconnect = config.getProperty("maxReconnect")?.toIntOrNull()
                    connectionFailMsg = "연결이 끊어졌습니다... 재접속 시도 중..."
                    var retry = 0
                    var connected = false
                    while (
                        WSHandler.sessionFailed == true &&
                        retry < (maxReconnect ?: 5) &&
                        !connected
                    ) {
                        connected = Tools.getConnected()
                        connectionFailMsg = """연결이 끊어졌습니다...
                                          |재접속 시도 중... ${++retry}/$maxReconnect""".trimMargin()
                        delay(3000)
                    }
                    if (connected) {
                        connectionFailMsg = "접속 완료. 다시 로그인 하는 중..."
                        while (!Tools.getConnected()) { 0 }
                        val result = WSHandler.sendLogin(MainUI.idInput, WSHandler.myKeyFile)
                        if (result != 0) {
                            returnToLogin = true
                            snackbarText = "로그인에 실패했습니다. 재시도 해주세요."
                            showSnackbar = true
                        } else {
                            snackbarText = "접속 완료!"
                            showSnackbar = true
                        }
                        MainUI.disconnectedUI = false
                    } else {
                        returnToLogin = true
                        snackbarText = "연결에 실패했습니다. 인터넷 연결 상태를 점검해 주세요."
                        showSnackbar = true
                        MainUI.disconnectedUI = false
                    }
                }
            }
        }
    }
}