package com.koisv.kcdesktop.ui

import androidx.compose.animation.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object MainUI {
    private val defaultPadding = Modifier.padding(16.dp)
    private val smallPadding = Modifier.padding(8.dp)
    private val checkboxPadding = Modifier.padding(start = 4.dp)
    private val endTextPadding = Modifier.padding(end = 8.dp)

    @Composable
    private fun InputField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier
        )
    }

    @Composable
    private fun HeaderBar(title: String) {

        TopAppBar(
            title = { Text(title) },
            backgroundColor = Color(100, 230, 100, 220),
            contentColor = Color.Black,
            elevation = 12.dp
        )
    }

    @Composable
    @Preview
    fun Register() {
        var id by remember { mutableStateOf("") }
        var nickname by remember { mutableStateOf("") }
        var otp by remember { mutableStateOf("") }
        var showSnackbar by remember { mutableStateOf(false) }

        MaterialTheme {
            Column(modifier = defaultPadding) {
                InputField(
                    value = id,
                    onValueChange = { id = it },
                    label = "ID",
                    modifier = smallPadding
                )
                InputField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = "Nickname [Optional]",
                    modifier = smallPadding
                )
                InputField(
                    value = otp,
                    onValueChange = { otp = it },
                    label = "OTP",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = {
                    println("ID: $id, Nickname: $nickname, OTP: $otp")
                    showSnackbar = true
                }) {
                    Text("Register")
                }
                SlideInSnackbar(text = "Registration Successful", visible = showSnackbar)
            }
        }

        // Automatically hide the Snackbar after 3 seconds
        LaunchedEffect(showSnackbar) {
            if (showSnackbar) {
                delay(3000)
                showSnackbar = false
            }
        }
    }

    @Composable
    @Preview
    fun Login() {
        var id by remember { mutableStateOf("") }
        var keepLogin by remember { mutableStateOf(false) }
        var showSnackbar by remember { mutableStateOf(false) }

        MaterialTheme {
            Scaffold(
                topBar = { HeaderBar("KoiChat Client [WSS]") }
            ) {
                Column(modifier = defaultPadding) {
                    InputField(
                        value = id,
                        onValueChange = { id = it },
                        label = "ID",
                        modifier = smallPadding
                    )
                    Row {
                        Checkbox(
                            checked = keepLogin,
                            onCheckedChange = { keepLogin = it },
                            modifier = checkboxPadding
                        )
                        Text("Keep me logged in", modifier = endTextPadding)
                        Button(onClick = {
                            println("ID: $id, Keep Login: $keepLogin")
                            showSnackbar = true
                        }) {
                            Text("Login")
                        }
                    }
                }
            }
        }

        // Automatically hide the Snackbar after 3 seconds
        LaunchedEffect(showSnackbar) {
            if (showSnackbar) {
                delay(3000)
                showSnackbar = false
            }
        }
    }

    @Composable
    fun SlideInSnackbar(text: String, visible: Boolean) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Snackbar {
                Card(
                    modifier = Modifier.padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = Color(100, 230, 100, 220)
                ) {
                    Text(
                        text,
                        modifier = Modifier.padding(4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
