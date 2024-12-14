package com.koisv.kcdesktop.ui

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
    private fun SnackbarAlert(text: String) {
        Snackbar(

        ) {
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
                }) {
                    Text("Register")
                }
            }
        }
    }

    @Composable
    @Preview
    fun Login() {
        var id by remember { mutableStateOf("") }
        var keepLogin by remember { mutableStateOf(false) }

        MaterialTheme {
            Scaffold(
                topBar = { HeaderBar("KoiChat Client [WSS]") }
            ) {
                SnackbarAlert("Hello World!")
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
                    }
                }
            }
        }
    }
}
