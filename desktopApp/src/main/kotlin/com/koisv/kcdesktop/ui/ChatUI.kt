package com.koisv.kcdesktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

object ChatUI {
    private val defaultPadding = Modifier.padding(16.dp)
    private val smallPadding = Modifier.padding(8.dp)
    private val checkboxPadding = Modifier.padding(start = 4.dp)
    private val endTextPadding = Modifier.padding(end = 8.dp)

    @Composable
    fun userListItem(users: List<String>, visible: Boolean) {
        val userLSize: Float by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            // Configure the animation duration and easing.
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.1f
            ),
            label = "size_ul"
        )
        Box(
            modifier = Modifier
                .padding(0.dp)
                .background(Color(100, 230, 100, 220))
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
            propagateMinConstraints = true
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (user in users) {
                    Column {
                        Text(
                            "사용자: $user",
                            modifier = Modifier.padding(start = 6.dp),
                            fontSize = (16 * userLSize).sp
                        )
                        Text(
                            "접속 시간: 1시간 30분",
                            modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                            fontSize = (10 * userLSize).sp
                        )
                    }
                    Divider(color = Color(0, 100, 0, 200), thickness = 1.dp)
                }
            }
        }
    }

    @Preview
    @Composable
    fun ChatScreen(connectedUsers: Int, userList: List<String>, connectionTime: String) {
        var showUsers by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        Scaffold(
            topBar = {
                TopAppBar(
                    backgroundColor = Color(100, 230, 100, 220),
                    title = { Text("접속자 수: $connectedUsers") },
                    actions = {
                        IconButton(onClick = { showUsers = !showUsers }) {
                            Icon(Icons.Default.Menu, contentDescription = "접속자 목록")
                        }
                    }
                )
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val userLSize: Float by animateFloatAsState(
                    targetValue = if (showUsers) 0.2f else 0f,
                    // Configure the animation duration and easing.
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = 0.1f
                    ),
                    label = "size_ul"
                )
                val scrollState = rememberScrollState()
                val coroutineScope = rememberCoroutineScope()
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
                            .padding(4.dp, 4.dp, 4.dp, height + 16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (i in 0..40) {
                            Divider(color = Color.LightGray, thickness = 1.dp)
                            Card {
                                Column {
                                    Text("사용자 $i : 채팅 내용 $i")
                                }
                            }
                            coroutineScope.launch {
                                scrollState.scrollTo(scrollState.maxValue)
                            }
                        }
                        Text("접속 시간: $connectionTime")
                    }
                    Divider(
                        modifier = Modifier.padding(bottom = height + 8.dp),
                        color = Color.DarkGray,
                        thickness = 2.25.dp
                    )

                    Row(
                        modifier =
                            Modifier.padding(top = 8.dp)
                                .fillMaxWidth()
                                .height(height),
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
                                height =
                                    if (height >= 148.dp && it.lines().size > 6) 148.dp
                                    else 48.dp + (((it.lines().size - 1) * 20).dp)
                            },
                            label = { Text("메시지 입력") },
                            modifier = Modifier
                                .fillMaxWidth((0.92 - (userLSize * 0.09)).toFloat())
                                .fillMaxHeight()
                        )
                        Button(
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(40, 195, 40, 220)),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .fillMaxSize(),
                            onClick = { println("전송: $message") },
                            shape = RoundedCornerShape(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
                        }
                    }
                }
                userListItem(userList, showUsers)
            }
        }
    }
}