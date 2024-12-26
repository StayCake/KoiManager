package com.koisv.kcdesktop

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koisv.kcdesktop.WSHandler.loggedInState
import com.koisv.kcdesktop.WSHandler.sessionKeyFolder
import com.koisv.kcdesktop.ui.ChatUI
import com.koisv.kcdesktop.ui.LoginUI
import com.koisv.kcdesktop.ui.MainUI
import com.koisv.kcdesktop.ui.Tools.painterResource
import io.ktor.websocket.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.io.encoding.ExperimentalEncodingApi

var wsDebug = false
val config = Properties()

@ExperimentalEncodingApi
@OptIn(InternalCoroutinesApi::class)
fun main(args: Array<String>) = application {
    val coroutine = rememberCoroutineScope()
    if (args.any{ it == "--debug"}) wsDebug = true

    val configFile = File("config.ini")
    if (configFile.exists()) {
        val configStream = configFile.inputStream()
        config.load(configStream)

        WSHandler.autoLogin = config.getProperty("autoLogin").toBoolean()
        WSHandler.autoLoginId = config.getProperty("autoLoginId")
        configStream.close()
    } else {
        println("Config file not found, creating new one")
        val prop = Properties()

        prop.setProperty("autoLogin", "false")
        prop.setProperty("autoLoginId", "")
        prop.setProperty("maxReconnect", "5")

        prop.store(java.io.FileWriter("config.ini"), "KoiChat Desktop Client Config")
    }

    println("Debug mode: $wsDebug")
    Window(
        onCloseRequest = {
            if (loggedInState) {
                coroutine.launch {
                    WSHandler.sendLogout()
                    WSHandler.wsSession.close(
                        CloseReason(CloseReason.Codes.NORMAL, "Client closed")
                    )
                    WSHandler.wsClient.close()
                }
            }
            ::exitApplication.invoke()
        },
        icon = painterResource("icon.webp"),
        title = "KoiChat Client [WSS]",
    ) {
        val keys =
            if (sessionKeyFolder.exists() && sessionKeyFolder.listFiles().isNotEmpty()) {
                if (sessionKeyFolder.listFiles().size > 5) {
                    MainUI.keyFileExceed = true
                    WSHandler.getKeys().take(5)
                }
                else WSHandler.getKeys()
            } else {
                MainUI.isRegister = true
                MainUI.loginAlert = false
                emptyList()
            }
        val nav = rememberNavController()

        NavHost(
            navController = nav,
            startDestination = Nav.LOGIN.name
        ) {
            composable(Nav.LOGIN.name) { LoginUI.Authenticate(keys, nav) }
            composable(Nav.CHAT.name) { ChatUI.ChatScreen(nav) }
        }
    }
}

enum class Nav { LOGIN, CHAT }
