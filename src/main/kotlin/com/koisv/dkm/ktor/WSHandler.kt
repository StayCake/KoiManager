package com.koisv.dkm.ktor

import com.koisv.dkm.DataManager.WSChat
import com.koisv.dkm.DataManager.WSChat.ConType
import com.koisv.dkm.DataManager.compressDecRSA
import com.koisv.dkm.DataManager.compressEncRSA
import com.koisv.dkm.DataManager.decryptWithRSA
import com.koisv.dkm.DataManager.encryptWithRSA
import com.koisv.dkm.DataManager.hash
import com.koisv.dkm.ktor.WSHandler.ChatSession.ConResult.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ExperimentalEncodingApi
@ExperimentalUuidApi
@DelicateCoroutinesApi
object WSHandler {
    val logger: Logger = LogManager.getLogger("Ktor-Server")
    val sessionMap = mutableMapOf<WebSocketServerSession, ChatSession>()

    suspend fun statusUpdate() {
        sessionMap.forEach { (session, chatSession) ->
            val loggedInUser = chatSession.loggedInWith
            val onlineUsers = WSChat.online.joinToString("|") {
                buildString {
                    append(it.userId)
                    append("%")
                    append(if (it.conType == ConType.PC) "0" else "1")
                    append("%")
                    append(it.lastLogin)
                    if (it.nick != null) {
                        append("%")
                        append(it.nick)
                    }
                }
            }
            loggedInUser?.let {
                session.outgoing.send(Frame.Text("wsc:status||${onlineUsers.compressEncRSA(it.encKey)}"))
            }
        }
    }

    /**
     * A mutable list that stores the message history for a WebSocket chat application.
     *
     * Each entry in the list is a Triple containing:
     * - An `Instant` timestamp representing when the message was sent or received.
     * - An optional `WSCUser` representing the user who sent the message. This can be `null` if the sender is anonymous or unknown.
     * - A Pair consisting of the message content as a `String` and an optional `WSCUser`, representing the recipient user, if any. The recipient can be `null` if the message is broadcast
     *  or not directed to a specific user.
     *
     * This list helps in keeping a record of messages exchanged, including the timing and involved users, if available.
     */
    val messageHistory = mutableListOf<Triple<LocalDateTime, WSChat.WSCUser?, Pair<String, WSChat.WSCUser?>>>()

    init {
        if (messageHistory.isEmpty()) messageHistory.addAll(WSChat.loadMsgHistory().reversed())
        GlobalScope.launch {
            while (isActive) {
                delay(5.seconds)
                WSChat.online.removeAll { it.session?.isActive != true }
            }
        }
    }

    class RegisterFailedException(): IndexOutOfBoundsException()

    class ChatSession(val originIP: String, private val session: WebSocketServerSession) {
        var failCount = 0
        var loggedInWith: WSChat.WSCUser? = null
        val random = Random(Uuid.random().hashCode())
        val otpJob: Job = GlobalScope.launch {
            while (isActive && session.isActive) {
                delay(30.seconds)
                if (isActive && session.isActive) {
                    otpCode = random.nextInt(0, 9999).let {
                        val code = String.format(Locale.KOREAN, "%04d", it)
                        logger.info("OTP RENEWAL | {} | {}", originIP, code)
                        code.hash()
                    }
                }
            }
            otpCode = ""
        }
        var otpCode = ""

        init {
            otpJob.cancel()
        }

        /**
         * Represents the possible results of a connection attempt.
         *
         * DONE - Connection was successful.
         * FAIL_SAME_USER - Connection attempt failed due to the user already being connected.
         * FAIL_NO_USER - Connection attempt failed because the user does not exist.
         * FAIL_NO_PERMISSION - Connection attempt failed due to insufficient permissions.
         */
        enum class ConResult { DONE, FAIL_SAME_USER, FAIL_NO_USER, FAIL_NO_PERMISSION }

        fun changeNickname(user: WSChat.WSCUser, newNick: String?): ConResult {
            val userTargets = WSChat.online.filter { it.userId == user.userId }
            if (userTargets.isEmpty()) return FAIL_NO_USER

            if (newNick == null) logger.info("User {} removed their nickname", user.userId)
            else logger.info("User {} changed nickname to {}", user.userId, newNick)

            userTargets.forEach { it.nick = newNick }
            WSChat.online
                .filter { it.userId == user.userId }
                .forEach { it.nick = newNick }
            return DONE
        }

        fun rsaKeyCreate(): KeyPair {
            val keygen = KeyPairGenerator.getInstance("rsa")
            keygen.initialize(4096, SecureRandom())
            return keygen.genKeyPair()
        }

        @OptIn(ExperimentalEncodingApi::class)
        fun keyVerify(id: String, conType: ConType, encryptedUserId: String): Boolean {
            val loginTarget = WSChat.getWSCUser(id).firstOrNull { it.conType == conType }
            if (loginTarget == null) return false

            val decryptedId = encryptedUserId.decryptWithRSA(loginTarget.encKey)

            return decryptedId == id
        }

        suspend fun login(id: String, conType: ConType, encryptedUserId: String, session: WebSocketServerSession): ConResult {
            val loginTarget = WSChat.getWSCUser(id).firstOrNull { it.conType == conType }
                ?: return FAIL_NO_USER
            if (WSChat.online.any {it.userId == id && it.conType == conType}) return FAIL_SAME_USER

            return if (keyVerify(id, conType, encryptedUserId)) {
                logger.info("User {} Logged in at {}", id, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
                loggedInWith = loginTarget
                val newKey = rsaKeyCreate()
                loginTarget.session = session
                session.outgoing.send(Frame.Text(
                    "wsc:last_login||${loginTarget.lastLogin}${loginTarget.nick?.let{ "||$it" } ?: ""}"
                ))
                loginTarget.lastLogin = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                session.outgoing.send(Frame.Text(
                    "wsc:private_key_send||${Base64.encode(newKey.private.encoded).compressEncRSA(loginTarget.encKey)}||1"
                ))
                loginTarget.encKey = Base64.encode(newKey.public.encoded)
                WSChat.saveWSCUser(loginTarget)
                WSChat.online.add(loginTarget)
                DONE
            } else FAIL_NO_PERMISSION
        }

        suspend fun logout(user: WSChat.WSCUser): ConResult {
            val logoutTarget = WSChat.online.firstOrNull { it.userId == user.userId && it.conType == user.conType }
                ?: return FAIL_NO_USER

            logger.info("User {} Logged out at {}", user.userId, Clock.System.now())
            logoutTarget.session = null
            WSChat.online.remove(logoutTarget)
            loggedInWith = null
            statusUpdate()
            return DONE
        }

        suspend fun handleRecovery(otpCodeInput: String, conType: ConType, userId: String, session: WebSocketServerSession) {
            if (otpCodeInput == otpCode) {
                otpJob.cancel()
                val userKey = rsaKeyCreate()
                val existingUser = WSChat.online.firstOrNull { it.userId == userId && it.conType == conType }
                    ?: throw RegisterFailedException()
                existingUser.encKey = Base64.encode(userKey.public.encoded)
                session.outgoing.send(Frame.Text("wsc:private_key_send||${Base64.encode(userKey.private.encoded)}"))
                session.outgoing.send(Frame.Text("wsc:recovery_success"))
            } else {
                session.outgoing.send(Frame.Text("wsc:recovery_fail"))
            }
        }

        suspend fun registerUser(
            conType: ConType,
            otpCodeInput: String,
            userId: String,
            nickName: String?,
            session: WebSocketServerSession
        ): ConResult {
            return if (otpCodeInput == otpCode) {
                if (WSChat.getWSCUser(userId).isNotEmpty()) {
                    session.outgoing.send(Frame.Text("wsc:register_fail_same_user"))
                    return FAIL_SAME_USER
                }
                logger.info("User {} Registered at {}", userId, Clock.System.now())
                otpJob.cancel()
                val userKey = rsaKeyCreate()
                val newUser =
                    WSChat.WSCUser(
                        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        userId, nickName, Base64.encode(userKey.public.encoded),
                        conType, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()), session
                    )
                WSChat.saveWSCUser(newUser)
                WSChat.online.add(newUser)
                session.outgoing.send(Frame.Text("wsc:last_login||${newUser.lastLogin}${newUser.nick?.let{ "||$it" } ?: ""}"))
                session.outgoing.send(Frame.Text("wsc:private_key_send||${Base64.encode(userKey.private.encoded)}"))
                statusUpdate()
                DONE
            } else FAIL_NO_PERMISSION
        }

        suspend fun handleEncryptedMessage(
            encryptedMessage: String,
            recipientUser: String?
        ): ConResult {
            return try {
                val user = loggedInWith ?: return FAIL_NO_PERMISSION

                val decryptedMessage = encryptedMessage.compressDecRSA(user.encKey)
                if (decryptedMessage == null) return FAIL_NO_PERMISSION

                val recvUsers = recipientUser.let {
                    val forNicks = WSChat.online.filter { user -> user.nick == it }
                    val forIds = WSChat.online.filter { user -> user.userId == it }
                    (forNicks + forIds).ifEmpty { null }
                } ?: listOf()

                // Add the message to messageHistory
                val msgForm = Triple(
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                    user,
                    Pair(decryptedMessage, recvUsers.firstOrNull())
                )

                messageHistory.add(msgForm)
                WSChat.saveMsgHistory(msgForm)

                // Send message to recipientUser only if specified, otherwise to all online users
                if (recvUsers.isNotEmpty()) {
                    // Logic to encrypt message for recipientUser
                    recvUsers.forEach {
                        it.session?.outgoing?.send(
                            Frame.Text(
                                buildString {
                                    append("wsc:message_prv||")
                                    append(user.userId.encryptWithRSA(it.encKey))
                                    append("||")
                                    append(decryptedMessage.compressEncRSA(it.encKey))
                                    append("||")
                                    append(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString())
                                }
                            )
                        )
                    }
                } else {
                    WSChat.online.forEach {
                        it.session?.outgoing?.send(Frame.Text(buildString {
                            append("wsc:message||")
                            append((user.nick ?: user.userId).encryptWithRSA(it.encKey))
                            append("||")
                            append(decryptedMessage.compressEncRSA(it.encKey))
                            append("||")
                            append(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString())
                        }))
                    }
                }
                DONE
            } catch (e: Exception) {
                logger.error("Error processing message: {}", e.message)
                e.printStackTrace()
                FAIL_NO_PERMISSION // or another suitable error enum
            }
        }
    }

    suspend fun WebSocketServerSession.handle(data: String) {

        /**
         * "wsc: $action || data
         *
         * @action act for.
         * @conType [Int 0: PC, 1: Mobile]
         *
         * - data form -
         * code : <none>
         * register : otp_code.conType.user_id.nickname(optional)
         * login : user_id.conType.enc_user_id
         * logout : <none>
         * history : <none>
         * nick : new_nick(optional, null for clear)
         * recovery : same as register without nickname
         * chat : enc_message.prv_target (optional)
         *
         * - status message -
         * code_gen_successful
         * recovery_success
         * forbidden_nickname
         * register_[success|fail_nickname|fail_same_user|fail_no_permission]
         * login_[success|fail_no_user|fail_same_user|fail_no_permission|fail_blocked]
         * logout_[success|fail_no_user|fail_no_permission]
         * nick_change_[success|fail_no_user|fail_no_permission]
         * send_[complete|fail_no_permission|fail_unknown]
         * history_fail_login_required
         *
         * - return form -
         * last_login||time||nickname (optional)|... <encrypted>
         * history||time%sender(#)%chat%target(*)|... <encrypted>
         * private_key_send||key||1 (optional | 1: for login)
         * message||enc_sender||enc_message||time
         * message_prv||enc_sender||enc_message||time
         * status||user_id%onMobile%last_login%nickname (optional)|... <encrypted>
         *
         * - compress form -
         * %%sec_pass.data (encrypted)
         */

        if (data.startsWith("wsc:")) {
            val currentSession = sessionMap[this] ?: throw NullPointerException("Session is null")

            val rawData = data.split("||")
            val action = rawData[0].replace("wsc:", "")
            val paramRaw = data.replace(rawData[0] + "||", "")
            val params = paramRaw.split(".")
            when (action) {
                "code" -> {
                    currentSession.otpCode =
                        currentSession.random.nextInt(0, 9999).let {
                            val code = String.format(Locale.KOREAN, "%04d", it)
                            logger.info("OTP CODE | {} | {}", currentSession.originIP, code)
                            code.hash()
                        }
                    currentSession.otpJob.start()
                    outgoing.send(Frame.Text("wsc:code_gen_successful"))
                }
                "recovery" -> {
                    val otpCodeInput = params[0]
                    val conType = ConType.entries[params[1].toIntOrNull() ?: 0]
                    val userId = params.getOrNull(2) ?: throw RegisterFailedException()
                    currentSession.handleRecovery(otpCodeInput, conType, userId, this)
                    outgoing.send(Frame.Text("wsc:recovery_success"))
                }
                "register" -> {
                    val otpCodeInput = params[0]
                    val conType = ConType.entries[params[1].toIntOrNull() ?: 0]
                    val userId = params.getOrNull(2) ?: throw RegisterFailedException()
                    val nickName = params.getOrNull(3)?.ifBlank { null }
                    val nicknamePattern = Regex("^[\\p{L}\\p{N}\\s_-]+$")

                    if (!nickName.isNullOrEmpty() && !nicknamePattern.matches(nickName))
                        outgoing.send(Frame.Text("wsc:register_fail_nickname"))
                    else {
                        val result = currentSession.registerUser(conType, otpCodeInput, userId, nickName, this)
                        if (result == DONE) outgoing.send(Frame.Text("wsc:register_success"))
                        else outgoing.send(Frame.Text("wsc:register_fail_no_permission"))
                    }
                }
                "login" -> {
                    val userId = params[0]
                    val conType = ConType.entries[params[1].toIntOrNull() ?: 0]
                    val encryptedUserId = params[2]
                    val loginRes = currentSession.login(userId, conType, encryptedUserId, this)
                    when (loginRes) {
                        DONE -> outgoing.send(Frame.Text("wsc:login_success"))
                        FAIL_SAME_USER -> outgoing.send(Frame.Text("wsc:login_fail_same_user"))
                        FAIL_NO_USER -> outgoing.send(Frame.Text("wsc:login_fail_no_user"))
                        FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:login_fail_no_permission"))
                    }
                    if (loginRes != DONE)
                        if (currentSession.failCount > 5) {
                            outgoing.send(Frame.Text("wsc:login_fail_blocked"))
                            close(CloseReason(
                                CloseReason.Codes.VIOLATED_POLICY,
                                "Too many failed login attempts"
                            ))
                        }
                }
                "logout" -> {
                    val loggedInUser = currentSession.loggedInWith
                    val logoutRes = if (loggedInUser != null)
                        currentSession.logout(loggedInUser)
                    else FAIL_NO_USER

                    when (logoutRes) {
                        DONE -> outgoing.send(Frame.Text("wsc:logout_success"))
                        FAIL_NO_USER -> outgoing.send(Frame.Text("wsc:logout_fail_no_user"))
                        FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:logout_fail_no_permission"))
                        FAIL_SAME_USER -> false
                    }
                }
                "chat" -> {
                    val encryptedMessage = params[0]
                    val prvTarget = params.getOrNull(1)?.ifBlank { null }
                    val messageProcessResult =
                        currentSession.handleEncryptedMessage(encryptedMessage, prvTarget)

                    when (messageProcessResult) {
                        DONE -> outgoing.send(Frame.Text("wsc:send_success"))
                        FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:send_fail_no_permission"))
                        else -> outgoing.send(Frame.Text("wsc:send_fail_unknown"))
                    }
                }
                "history" -> {
                    val loggedInUser = currentSession.loggedInWith
                    if (loggedInUser == null) {
                        outgoing.send(Frame.Text("wsc:history_fail_login_required"))
                    } else {
                        val anonSymbol = "#"
                        val broadcastSymbol = "*"

                        val delimiter = "|"  // Choose a unique delimiter not present in Base64
                        val lastFiftyMessages = messageHistory.takeLast(50)

                        val formattedMessages = lastFiftyMessages.joinToString(delimiter) { (time, sender, content) ->
                            if (content.second?.userId != loggedInUser.userId)
                                buildString {
                                    append(time)
                                    append("%")
                                    append(sender?.nick ?: anonSymbol)
                                    append("%")
                                    append(content.first)
                                    append("%")
                                    append(content.second?.nick ?: broadcastSymbol)
                                }
                            else ""
                        }.ifEmpty { "empty" }.encryptWithRSA(loggedInUser.encKey)
                        outgoing.send(Frame.Text("wsc:history||$formattedMessages"))
                    }
                }
                "nick" -> {
                    val newNick = paramRaw.ifBlank { null }
                    val nicknamePattern = Regex("^[\\p{L}\\p{N}\\s_-]+$")

                    if (!newNick.isNullOrEmpty() && !nicknamePattern.matches(newNick)) {
                        outgoing.send(Frame.Text("wsc:forbidden_nickname"))
                    } else {
                        val loggedInUser = currentSession.loggedInWith
                        val nickChangeResult =
                            if (loggedInUser != null) currentSession.changeNickname(loggedInUser, newNick)
                            else FAIL_NO_USER

                        when (nickChangeResult) {
                            DONE -> outgoing.send(Frame.Text("wsc:nick_change_success"))
                            FAIL_NO_USER -> outgoing.send(Frame.Text("wsc:nick_change_fail_no_user"))
                            FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:nick_change_fail_no_permission"))
                            FAIL_SAME_USER -> false
                        }
                    }
                }
            }
            statusUpdate()
        }
        //send(data)
    }
}