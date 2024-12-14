@file:OptIn(ExperimentalEncodingApi::class)

package com.koisv.dkm.irc

import com.koisv.dkm.DataManager
import com.koisv.dkm.DataManager.hash
import com.koisv.dkm.irc.IRCChannelImpl.IRCChannel
import com.koisv.dkm.irc.IRCException.*
import com.koisv.dkm.irc.IRCMessageImpl.ClientMessage
import com.koisv.dkm.irc.IRCMessageImpl.MsgBuilder
import com.koisv.dkm.irc.IRCMessageImpl.ReplyCode.*
import com.koisv.dkm.irc.IRCMessageImpl.ServerMessage
import com.koisv.dkm.irc.IRCModeImpl.ChannelMode
import com.koisv.dkm.irc.IRCModeImpl.ChannelMode.*
import com.koisv.dkm.irc.IRCModeImpl.UserMode
import com.koisv.dkm.irc.IRCModeImpl.UserMode.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object IRCCommandImpl {
    suspend fun ClientMessage.execute(session: IRCSession) {
        val command = try {
            val exe = (Commands::class.nestedClasses as List<KClass<*>>).find { it.simpleName == command }
            (exe ?: throw  InvalidCommandException(command)).createInstance() as Executable
        } catch (_: Exception) {
            throw InvalidCommandException(message)
        }
        when {
            parameters.size < command.minimumParams ->
                session.send(
                    ServerMessage(
                        ircInstance.serverName,
                        ERR_NEEDMOREPARAMS,
                        "$command :Not enough parameters"
                    )
                )
            !session.isRegistered && !command.canExecuteUnregistered ->
                session.send(ServerMessage(ircInstance.serverName, ERR_NOTREGISTERED))
            else -> command.execute(session, this)
        }
    }

    class Parser(private val server: String) {
        @Throws(MissingCommandParametersException::class)
        fun parse(cmdMsg: String): ClientMessage {
            val components = cmdMsg.split(" ")
                .filterNot { it.isEmpty() }.toTypedArray()

            val command: String
            var prefix = server
            var paramIdx = 2
            val parameters = arrayListOf<String>()

            if (components.isEmpty()) throw MissingCommandParametersException()

            if (components[0].startsWith(":")) {
                prefix = components[0]
                command = components[1]
            } else {
                command = components[0]
                paramIdx = 1
            }

            for (i in paramIdx until components.size) {
                if (components[i].startsWith(":")) {
                    parameters.add(components.drop(i).joinToString(" "))
                    break
                }
                parameters.add(components[i])
            }
            return ClientMessage(command.uppercase(), cmdMsg, parameters, prefix)
        }
    }

    sealed interface Executable {
        val minimumParams: Int
        val canExecuteUnregistered: Boolean
        suspend fun execute(session: IRCSession, clientMessage: ClientMessage)
    }

    @Suppress("unused")
    object Commands {
        class JOIN: Executable {
            override val minimumParams: Int = 1
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val channel: String = clientMessage.parameters[0]
                val channelManager = ircInstance.channelManager
                val nick: String = session.client.nick
                val chan = channelManager.getChannel(channel)

                if (chan == null) {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_NOSUCHCHANNEL)
                            .withMessage("$nick $channel :No such channel")
                            .build()
                    )
                } else {
                    val channelUsers: MutableSet<IRCSession> = chan.users

                    if (channelUsers.indexOf(session) <= 0) {
                        chan.addUser(session)

                        val hostmask: String = session.client.hostMask
                        chan.broadcast(
                            MsgBuilder(hostmask)
                                .withReplyCode(RPL_JOIN)
                                .withMessage(chan.name)
                                .build()
                        )
                    }

                    this.sendChannelUsers(session, chan, clientMessage.commandOrigin)
                    val topicCommand = TOPIC()

                    topicCommand.sendTopic(ircInstance.serverName, session, chan)
                }
            }

            private suspend fun sendChannelUsers(session: IRCSession, channel: IRCChannel, origin: String) {
                val nicks: ArrayList<String> = channel.getNicks()
                val chanName: String = channel.name
                val nick: String = session.client.nick

                for (chanNick in nicks) {
                    session.send(
                        MsgBuilder(origin)
                            .withReplyCode(RPL_NAMREPLY)
                            .withMessage("$nick = $chanName :$chanNick")
                            .build()
                    )
                }

                session.send(
                    MsgBuilder(origin)
                        .withReplyCode(RPL_ENDOFNAMES)
                        .withMessage("$nick $chanName :End of NAMES list")
                        .build()
                )
            }
        }

        class MODE: Executable {
            override val minimumParams: Int = 1
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val target: String = clientMessage.parameters[0]
                val channelManager = ircInstance.channelManager

                if (channelManager.isChannelType(target))
                    Internal.CHANMODE().execute(session, clientMessage)
                else
                    Internal.USERMODE().execute(session, clientMessage)
            }
        }

        class MOTD: Executable {
            override val minimumParams: Int = 0
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val motd: String = ircInstance.config.motd

                if (motd.isBlank()) {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_NOMOTD)
                            .withMessage(session.client.nick)
                            .build()
                    )
                } else {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(RPL_MOTDSTART)
                            .withMessage(":- Message of the day - ")
                            .build()
                    )

                    val chunkedMessage = motd.replace("(.{80})".toRegex(), "$1\n")

                    for (message in chunkedMessage.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                        session.send(
                            MsgBuilder(ircInstance.serverName)
                                .withReplyCode(RPL_MOTD)
                                .withMessage(":- $message")
                                .build()
                        )
                    }

                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(RPL_ENDOFMOTD)
                            .withMessage(":End of the message of the day")
                            .build()
                    )
                }
            }
        }

        /**
         * Created by fbailey on 16/11/16.
         *
         * @TODO generate new unique id in place of nickname. That way,
         * at session registration if nick is taken unique id takes its place
         * and then client can automatically set new nick if need be.
         */
        class NICK: Executable {
            override val minimumParams: Int = 1
            override val canExecuteUnregistered: Boolean = true

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val nick: String = clientMessage.parameters[0]
                val oldHostMask: String = session.client.hostMask

                if (!isValidNick(nick, ircInstance.config.nickMaxLen)) {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_ERRONEOUSNICKNAME)
                            .build()
                    )
                } else {
                    if (ircInstance.findSession(nick) != null) {
                        session.send(
                            MsgBuilder(ircInstance.serverName)
                                .withReplyCode(ERR_NICKNAMEINUSE)
                                .withMessage(session.client.nick + " " + nick + " :Nickname already in use")
                                .build()
                        )

                        return
                    }
                    session.client.nick = nick

                    if (session.isRegistered) {
                        val channels: ArrayList<IRCChannel> = ircInstance.channelManager.getChannels(session)
                        val exclude = arrayListOf<IRCSession>()
                        exclude.add(session)

                        channels.forEach {
                            it.broadcast(
                                MsgBuilder(oldHostMask)
                                    .withReplyCode(RPL_NICK)
                                    .withMessage(nick)
                                    .build(), exclude
                            )
                        }

                        session.send(
                            MsgBuilder(oldHostMask)
                                .withReplyCode(RPL_NICK)
                                .withMessage(nick)
                                .build()
                        )
                    }
                }
            }

            private fun isValidNick(nick: String, maxLen: Int): Boolean = nick.length <= maxLen

        }

        class OPER: Executable {
            override val minimumParams: Int = 2
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val inputUsername: String = clientMessage.parameters[0]
                val inputPassword: String = clientMessage.parameters[1]


                val operators: List<IRCConfig.IRCCredentials> = DataManager.IRC.ircOps

                if (operators.any { it.equals(inputUsername.hash()) || it.equals(inputPassword.hash()) }) {
                    ircLogger.debug("{} identified. Adding mode {}.", inputUsername, OPERATOR.shortFlag)
                    session.modes.addMode(OPERATOR)

                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(RPL_YOUREOP)
                            .withMessage(session.client.nick + " :You are now an IRC operator")
                            .build()
                    )

                    val m = Internal.USERMODE()
                    m.sendUsermode(session, session)
                } else {
                    ircLogger.info("Failed discordOn attempt for: {}", inputUsername)
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_PASSWDMISMATCH)
                            .withMessage(session.client.nick + " :Invalid username or password")
                            .build()
                    )
                }
            }
        }

        class PART: Executable {
            override val minimumParams: Int = 1
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val target: String = clientMessage.parameters[0]

                val channelManager = ircInstance.channelManager
                val channel = channelManager.getChannel(target)

                if (channel == null) {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_NOSUCHCHANNEL)
                            .withMessage(session.client.nick)
                            .build()
                    )
                } else {
                    if (!channel.hasUser(session)) {
                        session.send(
                            MsgBuilder(ircInstance.serverName)
                                .withReplyCode(ERR_NOTONCHANNEL)
                                .withMessage(session.client.nick)
                                .build()
                        )
                    } else {
                        val message: String? =
                            if (clientMessage.parameters.size > 1) clientMessage.parameters[1] else null
                        this.partFromChannel(channel, session, message)
                    }
                }
            }

            suspend fun partFromChannel(channel: IRCChannel, session: IRCSession, partMessage: String?) {
                var message: String = channel.name
                if (partMessage != null && partMessage != "") message += " :$partMessage"

                channel.broadcast(
                    MsgBuilder(session.client.hostMask)
                        .withReplyCode(RPL_PART)
                        .withMessage(message)
                        .build()
                )
                channel.removeUser(session)
            }
        }

        class PING: Executable {
            override val minimumParams: Int = 1
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val serverName: String = ircInstance.serverName

                if (clientMessage.parameters.size < 2) {
                    session.send(
                        MsgBuilder(serverName)
                            .withReplyCode(RPL_PONG)
                            .withMessage(clientMessage.parameters[0])
                            .build()
                    )
                } else {
                    session.send(
                        MsgBuilder(serverName)
                            .withReplyCode(ERR_NOSUCHSERVER)
                            .withMessage(clientMessage.parameters[0] + " " + clientMessage.parameters[1])
                            .build()
                    )
                }
            }
        }

        class PRIVMSG: Executable {
            override val minimumParams: Int = 2
            override val canExecuteUnregistered: Boolean = false
            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val target: String = clientMessage.parameters[0]
                val message: String = clientMessage.parameters[1]

                when {
                    ircInstance.channelManager.isChannel(target) ->
                        ircInstance.channelManager.getChannel(target)
                            ?.let { sendChannelMessage(session, it, target, message) }
                    ircInstance.channelManager.isChannelType(target) -> {
                        session.send(
                            MsgBuilder(ircInstance.serverName)
                                .withReplyCode(ERR_NOSUCHCHANNEL)
                                .withMessage(session.client.nick)
                                .build()
                        )
                    }
                    else -> {
                        val targetSession: IRCSession? = ircInstance.findSession(target)
                        sendPrivateMessage(session, targetSession, message)
                    }
                }
            }

            private suspend fun sendChannelMessage(session: IRCSession, channel: IRCChannel, target: String, message: String) {
                if (channel.hasUser(session)) {
                    val excluded = ArrayList<IRCSession>()
                    excluded.add(session)

                    channel.broadcast(
                        MsgBuilder(session.client.hostMask)
                            .withReplyCode(RPL_PRIVMSG)
                            .withMessage("$target :$message")
                            .build(),
                        excluded
                    )
                } else {
                    session.send(
                        MsgBuilder(session.client.hostMask)
                            .withReplyCode(ERR_NOTONCHANNEL)
                            .withMessage("${session.client.nick} :not on channel")
                            .build()
                    )
                }
            }

            private suspend fun sendPrivateMessage(
                session: IRCSession,
                targetSession: IRCSession?,
                message: String
            ) {
                if (targetSession == null) {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_NOSUCHNICK)
                            .withMessage(session.client.nick)
                            .build()
                    )
                } else {
                    targetSession.send(
                        MsgBuilder(session.client.hostMask)
                            .withReplyCode(RPL_PRIVMSG)
                            .withMessage("${targetSession.client.nick} :$message")
                            .build()
                    )
                }
            }
        }

        class QUIT: Executable {
            override val minimumParams: Int = 0
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val channels: ArrayList<IRCChannel> = ircInstance.channelManager.getChannels(session)
                val exclude = ArrayList<IRCSession>()
                exclude.add(session)

                var message = ""

                if (clientMessage.parameters.isNotEmpty()) message = clientMessage.parameters[0]

                for (chan in channels) {
                    val command = PART()
                    command.partFromChannel(chan, session, message)
                }

                session.send(
                    MsgBuilder(session.client.hostMask)
                        .withReplyCode(RPL_QUIT)
                        .withMessage(":$message")
                        .build()
                )

                ircInstance.closeSession(session)
            }
        }

        class TOPIC: Executable {
            override val minimumParams: Int = 1
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val target = clientMessage.parameters[0]
                val channel = ircInstance.channelManager.getChannel(target)

                if (channel == null) {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_NOSUCHCHANNEL)
                            .build()
                    )
                } else if (channel.hasMode(any = setOf(SECRET, PRIVATE))) {
                    ircLogger.info("Channel: {} is marked as secret/private. Ignoring TOPIC command", channel.name)
                } else if (!channel.hasUser(session)) {
                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(ERR_NOTONCHANNEL)
                            .build()
                    )
                } else {
                    if (clientMessage.parameters.size < 2) {
                        this.sendTopic("", session, channel)
                    } else {
                        val userModes: IRCModeImpl.ModeSet = session.modes

                        val canSetTopic =
                            userModes.hasMode(OPERATOR) || userModes.hasMode(LOCAL_OPERATOR) ||
                                    channel.hasModeForUser(session, CHAN_OPERATOR) || channel.hasModeForUser(session, OWNER)

                        if (!canSetTopic && channel.hasMode(OP_TOPIC_ONLY)) {
                            ircLogger.info(
                                "User: {} tried to set topic on: {} with insufficient permissions",
                                session.client.hostMask,
                                channel.name
                            )

                            session.send(
                                MsgBuilder(ircInstance.serverName)
                                    .withReplyCode(ERR_CHANOPRIVSNEEDED)
                                    .withMessage(channel.name)
                                    .build()
                            )
                        } else {
                            val topic: String = clientMessage.parameters[1]
                            val author: String = session.client.hostMask
                            channel.setTopic(topic, author)

                            ircLogger.info("{} set topic to: {}", author, topic)

                            for (user in channel.users) {
                                this.sendTopicChange(author, user, channel)
                            }
                        }
                    }
                }
            }

            suspend fun sendTopic(origin: String, session: IRCSession, channel: IRCChannel) {
                val topic: String = channel.topic
                val nick: String = session.client.nick

                val message: ServerMessage = if (topic.isBlank()) {
                    MsgBuilder(origin)
                        .withReplyCode(RPL_NOTOPIC)
                        .withMessage("$nick ${channel.name} :No topic is set")
                        .build()
                } else {
                    MsgBuilder(origin)
                        .withReplyCode(RPL_TOPIC)
                        .withMessage("$nick ${channel.name} :$topic")
                        .build()
                }

                session.send(message)
            }

            private suspend fun sendTopicChange(origin: String, session: IRCSession, channel: IRCChannel) {
                session.send(
                    MsgBuilder(origin)
                        .withReplyCode(RPL_TOPIC_CHANGE)
                        .withMessage("${channel.name} :${channel.topic}")
                        .build()
                )
            }
        }

        class USER: Executable {
            override val minimumParams: Int = 4
            override val canExecuteUnregistered: Boolean = true

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val serverName: String = ircInstance.serverName

                if (session.isRegistered) session.send(
                    MsgBuilder(serverName)
                        .withReplyCode(ERR_ALREADYREGISTERED)
                        .build()
                ) else {
                    val nick: String = session.client.nick
                    val username: String = clientMessage.parameters[0]
                    val hostName: String = session.client.hostName
                    val realName: String = clientMessage.parameters[3]

                    val ci = IRCSession.Client(nick, username, hostName, realName)
                    ircInstance.registerSession(session, ci)

                    session.send(MsgBuilder(serverName)
                        .withReplyCode(RPL_PONG)
                        .withMessage("PONG")
                        .build())

                    this.sendRegistrationAcknowledgement(session)
                }
            }

            private suspend fun sendRegistrationAcknowledgement(session: IRCSession) {
                val nick: String = session.client.nick
                val welcomeMessage = ircInstance.config.welcomeMsg
                val message =
                    buildString {
                        append("$nick :$welcomeMessage")
                        append("\nUser:: ")
                        append(session.client.hostMask)
                        append("\nYou are now logged on.")
                        append("\nUse /join for Talk.")
                    }
                val serverName: String = ircInstance.serverName

                session.send(
                    MsgBuilder(serverName)
                        .withReplyCode(RPL_WELCOME)
                        .withMessage(message)
                        .build()
                )
            }
        }

        class WHO: Executable {
            override val minimumParams: Int = 1
            override val canExecuteUnregistered: Boolean = false

            override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                val channel: IRCChannel? = ircInstance.channelManager.getChannel(clientMessage.parameters[0])

                if (channel != null) {
                    channel.users.forEach { user ->
                        if (!user.modes.hasMode(INVISIBLE)) {
                            val clientInfo: IRCSession.Client = user.client
                            var message: String = clientInfo.nick
                            message += " " + channel.name
                            message += " " + clientInfo.userName
                            message += " " + clientInfo.hostName
                            message += " " + ircInstance.serverName
                            message += " " + clientInfo.nick
                            message += " :0 " + clientInfo.realName // @TODO change hop count to be dynamic

                            session.send(
                                MsgBuilder(ircInstance.serverName)
                                    .withReplyCode(RPL_WHOREPLY)
                                    .withMessage(message)
                                    .build()
                            )
                        }
                    }

                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(RPL_ENDOFWHO)
                            .build()
                    )
                }
            }
        }

        object Internal {
            class CHANMODE : Executable {
                override val minimumParams: Int = 1
                override val canExecuteUnregistered: Boolean = false

                /**
                 * arg0 > Channel Name
                 * arg1 > Oper + Mods
                 * arg2 > SubArgs
                 */
                override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                    val chanName: String = clientMessage.parameters[0]
                    val channel = ircInstance.channelManager.getChannel(chanName)
                    val nick: String = session.client.nick

                    val serverMessage = MsgBuilder(ircInstance.serverName)

                    when {
                        channel == null -> session.send(
                            serverMessage
                                .withReplyCode(ERR_NOSUCHCHANNEL)
                                .withMessage("$nick $chanName :No such channel, can't change mode")
                                .build()
                        )

                        clientMessage.parameters.size < 2 -> {
                            val modes: String = channel.modes.stringModes

                            if (modes.isEmpty()) {
                                session.send(
                                    serverMessage
                                        .withReplyCode(ERR_NOCHANMODES)
                                        .withMessage(nick)
                                        .build()
                                )
                            } else {
                                session.send(
                                    serverMessage
                                        .withReplyCode(RPL_CHANNELMODEIS)
                                        .withMessage("$nick $chanName +${channel.modes}")
                                        .build()
                                )
                            }
                        }

                        else -> {
                            val modeRaw: String = clientMessage.parameters[1]
                            val message = "$nick ${channel.name}"

                            if (
                                !channel.hasModeForUser(session, CHAN_OPERATOR) &&
                                !channel.hasModeForUser(session, OWNER) &&
                                !session.modes.hasMode(OPERATOR)
                            ) {
                                session.send(
                                    serverMessage
                                        .withReplyCode(ERR_CHANOPRIVSNEEDED)
                                        .withMessage("$nick $chanName :Must be operator to change channel mode")
                                        .build()
                                )
                            } else {
                                val operation = modeRaw.dropLast(modeRaw.length - 1)

                                if (ChannelMode.has(operation))
                                    listChannelMode(session, channel, ChannelMode.get(operation))
                                else try {
                                    if ("+-".none { operation[0] == it }) throw InvalidModeOperationException()
                                    val modeFlags = modeRaw.substring(1)
                                    if (modeFlags.isBlank()) throw InvalidModeOperationException()

                                    val argModes = mutableSetOf<Triple<ChannelMode, Boolean, String>>()
                                    val singleModes = mutableSetOf<Pair<ChannelMode, Boolean>>()

                                    val rawArgs = clientMessage.parameters
                                    if (rawArgs.count { arg -> "+-".any { arg[0] == it } } > 1) {
                                        val chunks = rawArgs.drop(1)
                                        val argIdx = mutableSetOf<Int>()
                                        chunks.forEachIndexed { idx, chunk ->
                                            if (!argIdx.contains(idx)) {
                                                val chunkOp = chunk[0]
                                                val chunkModes = chunk.substring(1)
                                                var reqMode: ChannelMode? = null
                                                var reqModeCount = 0

                                                chunkModes.forEach { flag ->
                                                    val mode = ChannelMode.get(flag.toString()) as ChannelMode

                                                    if (mode.reqArgs) {
                                                        reqMode = mode
                                                        reqModeCount++
                                                    } else singleModes.add(Pair(mode, chunkOp == '+'))
                                                }

                                                if (reqModeCount > 1) throw InvalidModeOperationException()
                                                else reqMode?.let {
                                                    argModes.add(Triple(it, chunkOp == '+', chunks[idx + 1]))
                                                    argIdx.add(idx + 1)
                                                } ?: run {
                                                    if ("+-".none { it == chunks[idx + 1].getOrNull(0) })
                                                        throw InvalidModeOperationException()
                                                }
                                            }
                                        }
                                    } else {
                                        var argIdx = 2
                                        modeFlags.forEach {
                                            val load = ChannelMode.get(it.toString())
                                                ?: throw ModeNotFoundException(it.toString())
                                            if (load.reqArgs) {
                                                argModes.add(
                                                    Triple(
                                                        load as ChannelMode,
                                                        operation[0] == '+',
                                                        rawArgs[argIdx]
                                                    )
                                                )
                                                argIdx++
                                            } else {
                                                singleModes.add(Pair(load as ChannelMode, operation[0] == '+'))
                                            }
                                        }
                                    }

                                    argModes.forEach {
                                        if (it.second) it.first.addMode(channel, session, it.first, it.third)
                                        else it.first.removeMode(channel, session, it.first, it.third)
                                    }

                                    singleModes.forEach {
                                        if (it.second) it.first.addMode(channel, session, it.first)
                                        else it.first.removeMode(channel, session, it.first)
                                    }

                                    if (channel.modes.size > 0) {
                                        session.send(
                                            serverMessage
                                                .withReplyCode(RPL_CHANNELMODEIS)
                                                .withMessage("$message +${channel.modes.stringModes}")
                                                .build()
                                        )
                                    }
                                } catch (_: MissingModeArgumentException) {
                                    session.send(
                                        serverMessage
                                            .withReplyCode(ERR_NEEDMOREPARAMS)
                                            .withMessage("$nick :Missing mode argument")
                                            .build()
                                    )
                                } catch (e: ModeNotFoundException) {
                                    session.send(
                                        serverMessage
                                            .withReplyCode(ERR_UNKNOWNMODE)
                                            .withMessage("$nick ${e.message} :Unknown mode")
                                            .build()
                                    )
                                } catch (e: IRCActionException) {
                                    session.send(
                                        serverMessage
                                            .withReplyCode(e.replyCode)
                                            .withMessage(e.message)
                                            .build()
                                    )
                                } catch (_: Exception) {
                                    session.send(
                                        serverMessage
                                            .withReplyCode(ERR_NEEDMOREPARAMS)
                                            .withMessage("$nick :Unable to parse mode arguments")
                                            .build()
                                    )
                                }
                            }

                        }
                    }
                }

                /**
                 * We can list
                 * O - owner
                 * k - key
                 * l - user limit
                 * b - ban masks
                 * I - invite masks
                 * e - exception masks
                 *
                 * @TODO - to be implemented
                 * @param session
                 * @param channel
                 * @param mode
                 */
                private fun listChannelMode(session: IRCSession, channel: IRCChannel, mode: IRCModeImpl.Mode?) {}
            }

            class USERMODE : Executable {
                override val minimumParams = 1
                override val canExecuteUnregistered = false

                override suspend fun execute(session: IRCSession, clientMessage: ClientMessage) {
                    val targetNick: String = clientMessage.parameters[0]
                    val nick: String = session.client.nick

                    val target: IRCSession? = ircInstance.findSession(targetNick)

                    when {
                        targetNick != nick && !session.modes.hasMode(OPERATOR) -> {
                            session.send(
                                MsgBuilder(ircInstance.serverName)
                                    .withReplyCode(ERR_USERSDONTMATCH)
                                    .withMessage("$nick :Can't change mode for other users")
                                    .build()
                            )
                        }

                        target == null -> {
                            session.send(
                                MsgBuilder(ircInstance.serverName)
                                    .withReplyCode(ERR_NOSUCHNICK)
                                    .withMessage("$nick :No such user")
                                    .build()
                            )
                        }

                        clientMessage.parameters.size < 2 -> {
                            this.sendUsermode(session, target)
                        }

                        else -> {
                            val modeRaw: String = clientMessage.parameters[1]
                            val operation = modeRaw.getOrNull(0)
                            val mode = UserMode.get(modeRaw.getOrNull(1).toString())

                            if (modeRaw.length != 2 || mode == null || "+-".none { operation == it })
                                session.send(
                                    MsgBuilder(ircInstance.serverName)
                                        .withReplyCode(ERR_UMODEUNKNOWNFLAG)
                                        .withMessage("$nick :Unknown umode flag")
                                        .build()
                                )
                            else {
                                when (operation) {
                                    '+' -> (mode as UserMode).addMode(session, target, mode)
                                    '-' -> (mode as UserMode).addMode(session, target, mode)
                                }

                                sendUsermode(session, target)
                            }
                        }
                    }
                }

                suspend fun sendUsermode(session: IRCSession, target: IRCSession) {
                    val nick: String = target.client.nick
                    val modes: String = target.modes.toString()

                    session.send(
                        MsgBuilder(ircInstance.serverName)
                            .withReplyCode(RPL_UMODEIS)
                            .withMessage("$nick :+$modes")
                            .build()
                    )
                }
            }
        }
    }
}