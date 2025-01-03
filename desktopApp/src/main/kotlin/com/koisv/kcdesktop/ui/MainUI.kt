package com.koisv.kcdesktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.koisv.kcdesktop.WSHandler
import com.koisv.kcdesktop.WSHandler.loggedInWith
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object MainUI {

    val defaultPadding = Modifier.padding(16.dp)
    val smallPadding = Modifier.padding(8.dp)
    val iconShortPadding = Modifier.padding(2.dp, 0.dp, 2.dp, 0.dp)
    val iconPadding = Modifier.padding(2.dp)
    val iconTopPadding = Modifier.padding(0.dp, 2.dp, 8.dp, 0.dp)
    val checkboxPadding = Modifier.padding(start = 4.dp)
    val endTextPadding = Modifier.padding(end = 8.dp)
    val nameColor = Color(32, 116, 46)
    val recipientColor = Color(194, 145, 48)
    val focusColor = Color(3, 68, 12)
    val unfocusColor = Color(124, 161, 105)
    val backgroundColor1 = Color(100, 230, 100)
    val backgroundColor2 = Color(151, 210, 99)
    val backgroundGolor3 = Color(185, 207, 152)
    val serverMessageColor = Color(95, 95, 95)
    var disconnectedUI by mutableStateOf(false)

    // Shared
    var progressText by mutableStateOf("서버 연결 중...")
    var showSnackbar by mutableStateOf(false)
    var snackbarText by mutableStateOf("")
    var progressVisible by mutableStateOf(true)

    // on LoginUI.kt
    var idInput by mutableStateOf("")
    var nickInput by mutableStateOf("")
    var otpInput by mutableStateOf("")
    var isRegister by mutableStateOf(false)
    var isRecovery by mutableStateOf(false)
    var loginAlert by mutableStateOf(true)
    var keyFileExceed by mutableStateOf(false)

    // on ChatUI.kt
    var logoutDialog by mutableStateOf(false)
    var message by mutableStateOf(TextFieldValue(""))
    var showUsers by mutableStateOf(false)
    var nickDialog by mutableStateOf(false)

    /**
     * 사용자 컨텍스트 메뉴
     * @param open 메뉴 열림 여부
     * @param onClose 메뉴 닫은 후
     */
    @Composable
    fun WSHandler.WSCUser.UserCtxMenu(open: Boolean, onClose: () -> Unit) {
        val menuItems =
            if (loggedInWith?.id == id) listOf("닉네임 변겅", "로그아웃")
            else listOf("메시지 보내기")
        DropdownMenu(
            expanded = open,
            onDismissRequest = { onClose() },
            modifier = Modifier.background(backgroundColor1)
        ) {
            menuItems.forEach {
                DropdownMenuItem(onClick = {
                    when (it) {
                        "닉네임 변겅" -> nickDialog = true
                        "로그아웃" -> logoutDialog = true
                        "메시지 보내기" -> ChatUI.prvMsgTarget = this@UserCtxMenu
                    }
                    onClose()
                }) {
                    Row (horizontalArrangement = Arrangement.Center) {
                        when (it) {
                            "닉네임 변겅" -> Icon(
                                Icons.Default.Edit, contentDescription = "닉네임 변경",
                                modifier = iconTopPadding
                            )
                            "로그아웃" -> Icon(
                                Icons.AutoMirrored.Filled.Logout, contentDescription = "로그아웃",
                                modifier = iconTopPadding
                            )
                            "메시지 보내기" -> Icon(
                                Icons.AutoMirrored.Default.ForwardToInbox, contentDescription = "메시지 보내기",
                                modifier = iconTopPadding
                            )
                        }
                        Text(it)
                    }
                }
            }
        }
    }

    /**
     * 투명 버튼
     * @param text 버튼 텍스트
     * @param onClick 버튼 클릭 시
     */
    @Composable
    fun TransparentButton(
        text: String,
        onClick: () -> Unit
    ) {
        TextButton(
            onClick = { onClick() },
            colors = ButtonDefaults.buttonColors(
                contentColor = focusColor,
                backgroundColor = Color.Transparent
            )
        ) { Text(text) }
    }

    @Composable
    fun InputDialog(
        title: String,
        text: String,
        open: Boolean,
        onDone: ( data: String ) -> Unit,
        onClose: () -> Unit,
    ) {
        if (open) {
            val input = mutableStateOf(TextFieldValue(""))
            AlertDialog(
                onDismissRequest = { onClose() },
                title = {
                    Row(
                        Modifier.padding(4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "입력",
                            modifier = iconPadding
                        )
                        Text(title)
                    }
                },
                text = {
                    Column(
                        Modifier.padding(4.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(text)
                        TextField(
                            value = input.value,
                            onValueChange = { text -> input.value = text },
                            keyboardOptions = KeyboardOptions.Default,
                            keyboardActions = KeyboardActions(onDone = { onDone(input.value.text) }),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(4.dp),
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                focusedIndicatorColor = focusColor,
                                unfocusedIndicatorColor = unfocusColor,
                                cursorColor = focusColor,
                                unfocusedLabelColor = Color(59, 106, 59, 220),
                                focusedLabelColor = focusColor,
                            ),
                        )
                    }
                },
                buttons = {
                    Row {
                        TransparentButton("취소") { onClose() }
                        TransparentButton("확인") {
                            onDone(input.value.text)
                            onClose()
                        }
                    }
                },
                shape = RoundedCornerShape(2.dp),
                backgroundColor = backgroundGolor3,
                contentColor = Color.Black
            )
        }
    }

    /**
     * 확인용 다이얼로그
     * @param modifier 다이얼로그 꾸미기 값
     * @param icon 다이얼로그 아이콘
     * @param title 다이얼로그 제목
     * @param text 다이얼로그 내용
     * @param open 다이얼로그 열림 여부
     * @param onClose 다이얼로그 닫은 후
     * @param onConfirm 다이얼로그 확인 후
     */
    @Composable
    fun ConfirmDialog(
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        title: String,
        text: String? = null,
        open: Boolean,
        onClose: () -> Unit,
        onConfirm: () -> Unit
    ) {
        if (open) {
            AlertDialog(
                modifier = modifier,
                onDismissRequest = { onClose() },
                title = {
                    Row (horizontalArrangement = Arrangement.Center) {
                        if (icon != null) Icon(icon, contentDescription = text, modifier = iconTopPadding)
                        Text(title)
                    }
                },
                confirmButton = { TransparentButton("확인") { onConfirm() } },
                dismissButton = { TransparentButton("취소") { onClose() } },
                text = text?.let { { Text(it) } },
                shape = RoundedCornerShape(8.dp),
                backgroundColor = backgroundColor2,
                contentColor = Color.Black
            )
        }
    }

    /**
     * 로그인용 입력 필드
     * @param value 입력값
     * @param onValueChange 입력값 변경 시
     * @param label 입력 필드 미리보기 텍스트
     * @param modifier 입력 필드 꾸미기 값
     * @param isEnable 입력 필드 활성화
     * @param isPassword 비밀번호 필드 적용 여부
     * @param onDone 엔터키 입력 이벤트
     * @return 입력 필드
     */
    @Composable
    fun InputField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        isEnable: Boolean = true,
        isPassword: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier,
            enabled = isEnable,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions.Default,
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                focusedIndicatorColor = focusColor,
                unfocusedIndicatorColor = unfocusColor,
                cursorColor = focusColor,
                unfocusedLabelColor = Color(59, 106, 59, 220),
                focusedLabelColor = focusColor,
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }

    /**
     * 헤더 바
     * @param title 헤더 제목
     */
    @Composable
    fun HeaderBar(title: String) {
        TopAppBar(
            title = { Text(title) },
            backgroundColor = backgroundColor1,
            contentColor = Color.Black,
            elevation = 12.dp
        )
    }

    /**
     * 로딩 알림
     * @param visible 로딩 알림 표시 여부
     * @param text 로딩 알림 텍스트
     */
    @Composable
    fun ProgressAlert(visible: Boolean, text: String) {
        if (visible) {
            AlertDialog(
                onDismissRequest = { /* Do nothing */ },
                title = {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = focusColor)
                        Text(text, modifier = Modifier.padding(8.dp))
                    }
                },
                buttons = { /* No buttons */ },
                shape = RoundedCornerShape(8.dp),
                backgroundColor = backgroundGolor3,
                contentColor = Color.Black
            )
        }
    }

    /**
     * 알림 스낵바
     * @param text 스낵바 텍스트
     * @param visible 스낵바 표시 여부
     */
    @Composable
    fun AlertSnackBar(text: String, visible: Boolean) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Snackbar(
                backgroundColor = backgroundColor2
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(4.dp),
                    textAlign = TextAlign.Start,
                    color = Color.Black
                )
            }
        }
    }

    /**
     * 취소 가능 스낵바
     * @param text 스낵바 텍스트
     * @param visible 스낵바 표시 여부
     * @param onCancel 스낵바 취소 시
     */
    @Composable
    fun CancelableSnackBar(text: String, visible: Boolean, onCancel: () -> Unit) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Snackbar(
                backgroundColor = backgroundColor2,
                action = {
                    TextButton(onClick = { onCancel() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "취소",
                            tint = nameColor
                        )
                    }
                }
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(4.dp),
                    textAlign = TextAlign.Start,
                    color = Color.Black
                )
            }
        }
    }
}