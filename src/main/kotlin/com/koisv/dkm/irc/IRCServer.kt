@file:OptIn(ExperimentalEncodingApi::class)

package com.koisv.dkm.irc

import com.koisv.dkm.DataManager
import com.koisv.dkm.irc.IRCCommandImpl.execute
import com.koisv.dkm.irc.IRCMessageImpl.ClientMessage
import com.koisv.dkm.irc.IRCMessageImpl.ReplyCode.ERR_NEEDMOREPARAMS
import com.koisv.dkm.irc.IRCMessageImpl.ReplyCode.ERR_UNKNOWNCOMMAND
import com.koisv.dkm.irc.IRCMessageImpl.ServerMessage
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.IOException
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

lateinit var ircInstance: IRCServer
val ircLogger: Logger = LogManager.getLogger("IRC")

@OptIn(ExperimentalUuidApi::class)
class IRCServer(val port: Int) {
    val config = DataManager.IRC.loadConfig()
    val serverName: String = config.serverName
    val channelManager = IRCChannelImpl.IRCChannelManager(config)
    private val commandParser = IRCCommandImpl.Parser(serverName)
    private val activeSessions = arrayListOf<IRCSession>()
    private val registeredSessions = arrayListOf<IRCSession>()

    init {
        ircInstance = this
        startIRClogger()
    }

    private fun startIRClogger() {
        try {
            ircLogger.info("IRC Starting...")
        } catch (e: IOException) {
            logError("IRC 서버 가동에 실패했습니다", e)
        }
    }

    private fun logError(message: String, exception: Exception) {
        ircLogger.error("$message : {}", exception.stackTrace)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun listen(): Job = withContext(Dispatchers.IO) {
        launch {
            try {
                val serverSocket = aSocket(SelectorManager(Dispatchers.IO)).tcp().bind(port = port)
                handleIncomingConnections(serverSocket)
            } catch (e: IOException) {
                logError("Unable to accept connection", e)
            }
        }
    }

    private suspend fun handleIncomingConnections(serverSocket: ServerSocket) {
        while (true) {
            val clientSocket = serverSocket.accept()
            val sessionId = Uuid.random().toString()
            val newSession = IRCSession(clientSocket, sessionId)
            newSession.run()
            activeSessions.add(newSession)
        }
    }

    fun isChannelMode(shortFlag: String): Boolean =
        IRCModeImpl.ChannelMode.get(shortFlag) is IRCModeImpl.ChannelMode

    fun isUserMode(shortFlag: String): Boolean =
        IRCModeImpl.UserMode.get(shortFlag) is IRCModeImpl.UserMode

    suspend fun receive(session: IRCSession, command: String) {
        try {
            val message = commandParser.parse(command)
            handleParsedMessage(session, message)
        } catch (_: IRCException.MissingCommandParametersException) {
            session.send(ServerMessage(this.serverName, ERR_NEEDMOREPARAMS, ":Not enough parameters"))
        } catch (_: IRCException.InvalidCommandException) {
            handleInvalidCommandException(session)
        }
    }

    private suspend fun handleParsedMessage(session: IRCSession, message: ClientMessage) {
        if (message.command == "OPER")
            ircLogger.debug("***REDACTED - OPER***")
        else
            ircLogger.debug(message.message)

        message.execute(session)
    }

    private suspend fun handleInvalidCommandException(session: IRCSession) {
        val responseMessage = if (session.isRegistered) session.client.hostMask else ""
        session.send(ServerMessage(this.serverName, ERR_UNKNOWNCOMMAND, responseMessage))
    }

    @Synchronized
    fun closeSession(session: IRCSession) {
        try {
            activeSessions.remove(session)
            registeredSessions.remove(session)
            session.terminate()
        } catch (e: Exception) {
            logError("An error occurred while closing connection", e)
        }
    }

    @Synchronized
    fun registerSession(session: IRCSession, client: IRCSession.Client) {
        session.register(client)
        registeredSessions.add(session)
        activeSessions.remove(session)
    }

    @Synchronized
    fun findSession(byNick: String): IRCSession? =
        registeredSessions.firstOrNull { it.client.nick == byNick }

    suspend fun broadcast(message: ServerMessage) =
        registeredSessions.forEach { it.send(message) }
}