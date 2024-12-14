package com.koisv.kcdesktop

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

@OptIn(DelicateCoroutinesApi::class)
object WSHandler {
    val wsClient = HttpClient(CIO) {
        install(WebSockets)
    }

    val sessionKeyFile = File("./user.pem")


    init {
        GlobalScope.launch { startWS() }
    }

    lateinit var wsSession: ClientWebSocketSession
    val sessionOpened get() = this::wsSession.isInitialized



    suspend fun startWS() = coroutineScope {
        if (wsDebug) wsClient.ws(host = "localhost", path = "/") {
            wsSession = this
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val rawData = frame.readText()

                        if (rawData.startsWith("wsc:")) {
                            if (rawData.contains("||")) {
                                val data = rawData.split("||")
                                val action = data[0].replace("wsc:", "")
                                when (action) {
                                    "message" -> {

                                    }
                                }
                            } else {
                                val status = rawData.replace("wsc:", "")
                                when (status) {

                                }
                            }
                        }
                    }
                    else -> {
                        outgoing.send(Frame.Text("Not Yet Implemented"))
                    }
                }
            }
        }
        else wsClient.wss(host = "ws.koisv.com", path = "/") {
            println("Connected to WS")
        }
    }
}