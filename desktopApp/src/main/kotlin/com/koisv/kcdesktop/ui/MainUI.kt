package com.koisv.kcdesktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val checkboxPadding = Modifier.padding(start = 4.dp)
    val endTextPadding = Modifier.padding(end = 8.dp)
    val focusColor = Color(57, 88, 68)
    val unfocusColor = Color(100, 230, 100, 220)
    val background = Color(154, 229, 154, 220)
    var disconnectedUI by mutableStateOf(false)

    // Shared
    var progressText by mutableStateOf("서버 연결 중...")
    var showSnackbar by mutableStateOf(false)
    var snackbarText by mutableStateOf("")
    var progressVisible by mutableStateOf(true)

    // on LoginUI.kt
    var id by mutableStateOf("")
    var nickname by mutableStateOf("")
    var otp by mutableStateOf("")
    var isRegister by mutableStateOf(false)
    var loginAlert by mutableStateOf(true)
    var keyFileExceed by mutableStateOf(false)

    // on ChatUI.kt
    var logoutDialog by mutableStateOf(false)
    var message by mutableStateOf(TextFieldValue(""))
    var showUsers by mutableStateOf(false)

    @Composable
    fun UserCTXMenu(open: Boolean, selectedUser: WSHandler.WSCUser, onClose: () -> Unit) {
        val menuItems =
            if (selectedUser.id == loggedInWith?.id) listOf("닉네임 변겅", "로그아웃")
            else listOf("메시지 보내기")
        DropdownMenu(
            expanded = open,
            onDismissRequest = { onClose() },
            modifier = Modifier.background(Color(100, 230, 100, 220))
        ) {
            menuItems.forEach {
                DropdownMenuItem(onClick = {
                    when (it) {
                        "닉네임 변겅" -> {
                            message = message.copy(text = "/nick ")
                        }
                        "로그아웃" -> {
                            onClose()
                            showUsers = false
                            logoutDialog = true
                        }
                        "메시지 보내기" -> {
                            message = message.copy(text = "/w ${selectedUser.id} ")
                        }
                    }
                }) {
                    Text(it)
                }
            }
        }
    }

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
        ) {
            Text(text)
        }
    }

    @Composable
    fun ConfirmDialog(
        modifier: Modifier = Modifier,
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
                title = { Text(title) },
                confirmButton = { TransparentButton("확인") { onConfirm() } },
                dismissButton = { TransparentButton("취소") { onClose() } },
                text = text?.let { { Text(it) } },
                shape = RoundedCornerShape(8.dp),
                backgroundColor = Color(100, 230, 100, 220),
                contentColor = Color.Black
            )
        }
    }

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
        TextField(
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
                backgroundColor = background,
                focusedIndicatorColor = focusColor,
                unfocusedIndicatorColor = unfocusColor,
                cursorColor = focusColor,
                unfocusedLabelColor = Color(59, 106, 59, 220),
                focusedLabelColor = focusColor,
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }

    @Composable
    fun HeaderBar(title: String) {
        TopAppBar(
            title = { Text(title) },
            backgroundColor = Color(100, 230, 100, 220),
            contentColor = Color.Black,
            elevation = 12.dp
        )
    }

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
                backgroundColor = Color(100, 230, 100, 220),
                contentColor = Color.Black
            )
        }
    }

    @Composable
    fun SlideInSnackbar(text: String, visible: Boolean) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Snackbar(
                backgroundColor = Color(120, 250, 110, 220)
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(4.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
            }
        }
    }
}