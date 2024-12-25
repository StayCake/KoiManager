package com.koisv.dkm.irc

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
data class IRCSession(
    val socket: Socket,
    val id: String
): IRCModeImpl.Moddable {
    data class Client(
        var nick: String = "*",
        var userName: String = "*",
        var hostName: String = "*",
        var realName: String = "*"
    ) {
        var hostMask = "$nick!$userName@$hostName"

        fun update() { hostMask = "$nick!$userName@$hostName" }
    }

    val client = Client(hostName = (socket.remoteAddress as InetSocketAddress).hostname)
        .apply { update() }

    private val input by lazy { socket.openReadChannel() }
    private val output by lazy { socket.openWriteChannel() }

    private var isTerminated: Boolean = false
    fun terminate() { isTerminated = true }

    var isRegistered: Boolean = false
    override val modes: IRCModeImpl.ModeSet = IRCModeImpl.ModeSet()

    fun register(client: Client) {
        this.client.apply {
            nick = client.nick
            userName = client.userName
            realName = client.realName
            update()
        }
        isRegistered = true
    }

    suspend fun send(message: IRCMessageImpl.Sendable) {
        val final = message.compile()
        ircLogger.debug("Send: {}", final)

        try {
            output.writeStringUtf8(final)
            output.flush()
        } catch (e: IOException) { errorCleanUp(e) }
    }

    fun run() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (!isTerminated) process(input.readUTF8Line())
                socket.close()
            } catch (e: IOException) { errorCleanUp(e) }
        }
    }

    private fun errorCleanUp(e: IOException) {
        ircLogger.error("Connection Closed Unexpectedly.")
        ircLogger.error("Cleaning Up...")
        ircLogger.debug(e.stackTrace)
        ircInstance.closeSession(this)
    }

    private suspend fun process(command: String?) {
        command?.let { ircInstance.receive(this, command) }
            ?: run { ircInstance.closeSession(this) }
    }
}
