package com.koisv.kcdesktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koisv.kcdesktop.WSHandler
import com.koisv.kcdesktop.WSHandler.loggedInWith
import com.koisv.kcdesktop.WSHandler.messages
import com.koisv.kcdesktop.WSHandler.onlines
import com.koisv.kcdesktop.ui.MainUI.SlideInSnackbar
import com.koisv.kcdesktop.ui.MainUI.showSnackbar
import com.koisv.kcdesktop.ui.MainUI.snackbarText
import com.koisv.kcdesktop.ui.Tools.chatTimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object ChatUI {
    var update by mutableStateOf(0.0f)

    private val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = 0.1f
    )

    private var message by mutableStateOf(TextFieldValue(""))

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

    fun messageSend(coroutineScope: CoroutineScope) {
        if (message.text.isNotBlank())
            coroutineScope.launch { WSHandler.sendMessage(message.text) }
                .invokeOnCompletion { message = message.copy(text = "") }
        else {
            snackbarText = "빈 메시지는 보낼 수 없습니다!"
            showSnackbar = true
        }
    }

    @Composable
    fun userListItem(visible: Boolean) {
        val userScrollState = rememberScrollState()
        val userLSize: Float by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = animationSpec,
            label = "size_ul"
        )
        Box(
            modifier = Modifier
                .background(Color(100, 230, 100, 220))
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
                    val onlineTime = mutableStateOf(getRelativeTime(user.lastOnline))
                    Row {
                        if (user.onMobile) Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = "모바일",
                            modifier = Modifier.padding(start = 4.dp)
                        ) else Icon(
                            Icons.Default.Computer,
                            contentDescription = "PC",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Column {
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
                        }
                    }
                    Divider(color = Color(0, 100, 0, 200), thickness = 1.dp)
                }
            }
        }
    }

    @Preview
    @Composable
    fun ChatScreen() {
        var showUsers by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        Scaffold(
            topBar = {
                TopAppBar(
                    backgroundColor = Color(100, 230, 100, 220),
                    title = { Text(
                        "온라인 | ${loggedInWith.nickname ?: loggedInWith.id} | 접속자 수: ${onlines.size}"
                    ) },
                    actions = {
                        IconButton(onClick = { showUsers = !showUsers }) {
                            Icon(Icons.Default.Person, contentDescription = "접속자 목록")
                        }
                    }
                )
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val userLSize: Float by animateFloatAsState(
                    targetValue = if (showUsers) 0.2f else 0f,
                    animationSpec = animationSpec,
                    label = "size_ul"
                )
                val scrollState = rememberScrollState()
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
                            .padding(bottom = height + 16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (i in messages) {
                            Divider(color = Color.LightGray, thickness = 1.dp)
                            Card { Column {
                                Text("[${i.second.format(chatTimeFormat)}] ${i.first} : ${i.third.first}")
                            } }
                            coroutineScope.launch { scrollState.scrollTo(scrollState.maxValue) }
                        }
                        SlideInSnackbar(text = snackbarText, visible = showSnackbar)
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
                                backgroundColor = Color(100, 225, 100, 220),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            onValueChange = {
                                message = it
                                height = heightCalculate(height, it.text)
                            },
                            label = { Text("메시지 입력") },
                            modifier = Modifier
                                .fillMaxWidth(0.92f - (userLSize * 0.09f))
                                .onPreviewKeyEvent {
                                    if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                                        if (it.isShiftPressed) {
                                            message = message.copy(text = message.text + "\n")
                                            message = message.copy(selection = TextRange(message.text.length))
                                            height = heightCalculate(height, message.text)
                                        }
                                        else messageSend(coroutineScope)
                                        true
                                    } else false
                                },
                            keyboardActions = KeyboardActions(onDone = { messageSend(coroutineScope) })
                        )
                        Button(
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(40, 195, 40, 220)),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .height(48.dp)
                                .fillMaxWidth(),
                            onClick = { messageSend(coroutineScope) },
                            shape = RoundedCornerShape(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
                        }
                    }
                }
                userListItem(showUsers)
            }
            LaunchedEffect(showSnackbar) {
                if (showSnackbar) {
                    delay(3000)
                    showSnackbar = false
                }
            }
        }
    }
    }