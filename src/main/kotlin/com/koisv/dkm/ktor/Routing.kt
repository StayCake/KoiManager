package com.koisv.dkm.ktor

import com.koisv.dkm.DataManager
import com.koisv.dkm.ktor.WSHandler.handle
import com.koisv.dkm.ktor.WSHandler.sessionMap
import com.koisv.dkm.ktor.WSHandler.statusUpdate
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import org.apache.logging.log4j.LogManager
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@ExperimentalUuidApi
@ExperimentalEncodingApi
@DelicateCoroutinesApi
fun Application.configureRouting() {
    val logger = LogManager.getLogger("Ktor-Server")
    install(WebSockets) {
        pingPeriod = 5.seconds
        timeoutMillis = 5000
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket("/") {
            val originIP = call.request.origin.remoteAddress
            if (!sessionMap.containsKey(this))
                sessionMap[this] = WSHandler.ChatSession(originIP, this)

            logger.info("웹소켓 연결 됨 - {}", originIP)
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> this.handle(frame.readText())
                    else -> {
                        outgoing.send(Frame.Text("Not Yet Implemented"))
                    }
                }
            }
            DataManager.WSChat.online.removeAll { it.session == this }
            sessionMap[this]?.otpJob?.cancel()
            sessionMap.remove(this)
            statusUpdate()
            logger.info("웹소켓 연결 종료 - {}", originIP)
        }
    }
}