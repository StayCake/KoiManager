package com.koisv.koichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import com.koisv.koichat.ui.theme.KoiManagerTheme

const val TEXT_FIELD_SHAPE = 50 // Rounded corner shape for TextFields
const val DEFAULT_SPACER_HEIGHT = 16 // Height used for consistent spacing

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoiManagerTheme {
                TopAppBar(title = { Text("KoiManager") })
                askToSignUp()
            }
        }
    }
}

@Composable
private fun askToSignUp() {
    var showDialog by remember { mutableStateOf(true) }
    var signUp by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Sign Up") },
            text = { Text("Do you want to sign up?") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    signUp = true
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showDialog = false; signUp = false }) {
                    Text("No")
                }
            }
        )
    } else if (signUp) registerScreen() // Navigate to MainScreen upon confirmation
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun loginScreen() {
    TopAppBar(title = { Text("KoiManager") })
    var id by remember { mutableStateOf("") }
    var doLogin by remember { mutableStateOf(false) }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ReusableTextField(value = id, onValueChange = {
                if (it.length <= 32) id = it
            }, hint = "ID")
            Spacer(modifier = Modifier.height(DEFAULT_SPACER_HEIGHT.dp))
            RememberMeRow()
            Spacer(modifier = Modifier.height(DEFAULT_SPACER_HEIGHT.dp))
            Button(
                onClick = { doLogin = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Login")
            }

        }
    }
    if (doLogin) {

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun registerScreen() {
    TopAppBar(title = { Text("KoiManager") })
    var id by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var doSignUp by remember { mutableStateOf(false) }
    Row {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                ReusableTextField(value = id, onValueChange = {
                    if (it.length <= 32) id = it
                }, hint = "ID")
                Spacer(modifier = Modifier.height(DEFAULT_SPACER_HEIGHT.dp))
                ReusableTextField(value = nickname, onValueChange = {
                    if (it.length <= 32) nickname = it
                }, hint = "Nickname")
                Spacer(modifier = Modifier.height(DEFAULT_SPACER_HEIGHT.dp))
                ReusableTextField(value = otp, onValueChange = {
                    if (it.length <= 4 && it.isDigitsOnly()) otp = it
                }, hint = "OTP")
                Spacer(modifier = Modifier.height(DEFAULT_SPACER_HEIGHT.dp))
                RememberMeRow()
                Spacer(modifier = Modifier.height(DEFAULT_SPACER_HEIGHT.dp))
                Button(
                    onClick = { doSignUp = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text("Sign Up")
                }
            }
        }
    }

    if (doSignUp) {

    }
}

@Composable
fun RememberMeRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = {}
        )
        Text(text = "Remember Me")
    }
}


@Composable
fun ReusableTextField(value: String, onValueChange: (String) -> Unit, hint: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(hint) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(TEXT_FIELD_SHAPE),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}