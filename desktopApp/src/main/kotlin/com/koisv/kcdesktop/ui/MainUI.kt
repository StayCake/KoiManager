package com.koisv.kcdesktop.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object MainUI {
    val defaultPadding = Modifier.padding(16.dp)
    val smallPadding = Modifier.padding(8.dp)
    val checkboxPadding = Modifier.padding(start = 4.dp)
    val endTextPadding = Modifier.padding(top = 8.dp, end = 8.dp)
    val transparentColor = Color.Transparent

    var id by mutableStateOf("")
    var nickname by mutableStateOf("")
    var otp by mutableStateOf("")
    var isRegister by mutableStateOf(false)
    var loginAlert by mutableStateOf(true)
    var keyFileExceed by mutableStateOf(false)

    var progressText by mutableStateOf("서버 연결 중...")
    var showSnackbar by mutableStateOf(true)
    var snackbarText by mutableStateOf("")
    var progressVisible by mutableStateOf(true)

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
                        CircularProgressIndicator()
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