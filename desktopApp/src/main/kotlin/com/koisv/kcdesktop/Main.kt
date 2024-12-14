package com.koisv.kcdesktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.koisv.kcdesktop.ui.ChatUI.ChatScreen
import com.koisv.kcdesktop.ui.MainUI.Register
import com.koisv.kcdesktop.ui.Tools.painterResource
import kotlinx.coroutines.InternalCoroutinesApi

var wsDebug = false

@OptIn(InternalCoroutinesApi::class)
fun main(args: Array<String>) = application {
    if (args.any{ it == "--debug"}) wsDebug = true
    Window(
        onCloseRequest = ::exitApplication,
        icon = painterResource("icon.webp"),
        title = "KoiChat Client [WSS]",
    ) {
        var doRegister by remember { mutableStateOf(false) }
        if (doRegister) Register()
        else ChatScreen(3, listOf("Koisv", "Koisv2", "Koisv3"), "1시간 30분")
    }
}
