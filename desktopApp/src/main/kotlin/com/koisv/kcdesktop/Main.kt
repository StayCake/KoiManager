package com.koisv.kcdesktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koisv.kcdesktop.Tools.painterResource
import com.koisv.kcdesktop.WSHandler.loggedInState
import com.koisv.kcdesktop.ui.ChatUI
import com.koisv.kcdesktop.ui.LoginUI
import io.ktor.websocket.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import java.awt.*
import java.awt.Frame
import java.io.File
import java.util.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.system.exitProcess

const val configFileName = "config.ini"
var wsDebug = false
val config = Properties()

@ExperimentalEncodingApi
@OptIn(InternalCoroutinesApi::class)
fun main(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        Dialog(Frame(), "오류").apply {
            layout = FlowLayout()
            add(Label("UI 외부 오류 발생: ${e.message}, ${e.cause}"))
            for (i in e.stackTrace) { add(Label(i.toString())) }
            val button = Button("OK").apply { addActionListener { dispose(); exitProcess(0) } }
            add(button)
            setSize(300,300)
            isVisible = true
        }
    }
    application {
        val logger = WSHandler.logger
        if (args.any { it == "--debug" }) wsDebug = true

        val configFile = File(configFileName)
        if (configFile.exists()) {
            val configStream = configFile.inputStream()
            config.load(configStream)

            WSHandler.autoLogin = config.getProperty("autoLogin").toBoolean()
            WSHandler.autoLoginId = config.getProperty("autoLoginId")
            configStream.close()
        } else {
            logger.debug("Config file not found, creating new one")
            val prop = Properties()

            prop.setProperty("autoLogin", "false")
            prop.setProperty("autoLoginId", "")
            prop.setProperty("maxReconnect", "5")

            prop.store(java.io.FileWriter(configFileName), "KoiChat Desktop Client Config")
        }

        logger.debug("Debug mode: $wsDebug")
        Window(
            onCloseRequest = {
                if (loggedInState) {
                    runBlocking {
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
            val nav = rememberNavController()

            NavHost(
                navController = nav,
                startDestination = Nav.LOGIN.name
            ) {
                composable(Nav.LOGIN.name) { LoginUI.Authenticate(nav) }
                composable(Nav.CHAT.name) { ChatUI.ChatScreen(nav) }
            }
        }
    }
}

enum class Nav { LOGIN, CHAT }
