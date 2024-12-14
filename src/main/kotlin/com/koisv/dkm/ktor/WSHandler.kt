package com.koisv.dkm.ktor

import com.koisv.dkm.DataManager.WSChat
import com.koisv.dkm.DataManager.WSChat.ConType
import com.koisv.dkm.DataManager.decryptWithRSA
import com.koisv.dkm.DataManager.encryptWithRSA
import com.koisv.dkm.DataManager.hash
import com.koisv.dkm.ktor.WSHandler.ChatSession.ConResult.*
import io.ktor.server.websocket.*
import io.ktor.util.encodeBase64
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ExperimentalEncodingApi
object WSHandler {
    val logger: Logger = LogManager.getLogger("Ktor-Server")
    val sessionMap = mutableMapOf<WebSocketServerSession, ChatSession>()


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
    val messageHistory = mutableListOf<Triple<Instant, WSChat.WSCUser?, Pair<String, WSChat.WSCUser?>>>()

    init {
        if (messageHistory.isEmpty()) messageHistory.addAll(WSChat.loadMsgHistory().reversed())
    }

    class RegisterFailedException(): IndexOutOfBoundsException()

    @OptIn(ExperimentalUuidApi::class, DelicateCoroutinesApi::class)
    class ChatSession(private val originIP: String) {
        var loggedInWith: WSChat.WSCUser? = null
        val random = Random(Uuid.random().hashCode())
        val otpJob: Job
        var otpCode = ""

        /**
         * Represents the possible results of a connection attempt.
         *
         * DONE - Connection was successful.
         * FAIL_SAME_USER - Connection attempt failed due to the user already being connected.
         * FAIL_NO_USER - Connection attempt failed because the user does not exist.
         * FAIL_NO_PERMISSION - Connection attempt failed due to insufficient permissions.
         */
        enum class ConResult { DONE, FAIL_SAME_USER, FAIL_NO_USER, FAIL_NO_PERMISSION }

        init {
            otpCode = random.nextInt(0, 9999).let {
                logger.info("OTP CODE | {} | {}", originIP, it)
                it.toString().hash()
            }

            otpJob = GlobalScope.async {
                while (isActive) {
                    otpCode = random.nextInt(0, 9999).let {
                        logger.info("OTP RENEWAL | {} | {}", originIP, it)
                        it.toString().hash()
                    }
                    delay(30.seconds)
                }
                otpCode = ""
            }
        }

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

        fun keyCreate(): KeyPair {
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
                logger.info("User {} Logged in at {}", id, Clock.System.now())
                loggedInWith = loginTarget
                val newKey = keyCreate()
                loginTarget.encKey = Base64.encode(newKey.public.encoded)
                loginTarget.session = session
                session.outgoing.send(Frame.Text("wsc:last_login||${loginTarget.lastLogin}"))
                loginTarget.lastLogin = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                WSChat.saveWSCUser(loginTarget)
                WSChat.online.add(loginTarget)
                session.outgoing.send(Frame.Text("wsc:private_key_send||${Base64.encode(newKey.private.encoded)}"))
                DONE
            } else FAIL_NO_PERMISSION
        }

        fun logout(user: WSChat.WSCUser): ConResult {
            val logoutTarget = WSChat.online.firstOrNull { it.userId == user.userId && it.conType == user.conType }
                ?: return FAIL_NO_USER

            logger.info("User {} Logged out at {}", user.userId, Clock.System.now())
            logoutTarget.session = null
            WSChat.online.remove(logoutTarget)
            loggedInWith = null
            return DONE
        }

        suspend fun handleRecovery(otpCodeInput: String, conType: ConType, userId: String, session: WebSocketServerSession) {
            if (otpCodeInput == otpCode) {
                otpJob.cancel()
                val userKey = keyCreate()
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
        ) {
            if (otpCodeInput == otpCode) {
                otpJob.cancel()
                val userKey = keyCreate()
                val newUser =
                    WSChat.WSCUser(
                        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        userId, nickName, Base64.encode(userKey.public.encoded),
                        conType, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()), session
                    )
                WSChat.saveWSCUser(newUser)
                WSChat.online.add(newUser)
                session.outgoing.send(Frame.Text("wsc:private_key_send||${Base64.encode(userKey.private.encoded)}"))
            }
        }

        suspend fun handleEncryptedMessage(
            encryptedMessage: String,
            session: WebSocketServerSession,
            recipientUser: List<WSChat.WSCUser>
        ): ConResult {
            return try {
                val user = loggedInWith ?: return FAIL_NO_PERMISSION

                val decryptedMessage = encryptedMessage.decryptWithRSA(user.encKey)

                // Add the message to messageHistory
                val msgForm = Triple(
                    Clock.System.now(),
                    user,
                    Pair(decryptedMessage, recipientUser.first())
                )

                messageHistory.add(msgForm)
                WSChat.saveMsgHistory(msgForm)

                // Send message to recipientUser only if specified, otherwise to all online users
                if (recipientUser.isNotEmpty()) {
                    // Logic to encrypt message for recipientUser
                    recipientUser.forEach {
                        session.outgoing.send(
                            Frame.Text(
                                "wsc:message_prv||${(user.userId+ "||" + decryptedMessage).encryptWithRSA(it.encKey)}"
                            )
                        )
                    }
                } else {
                    WSChat.online.forEach {
                        // Logic to encrypt message for each user
                        session.outgoing.send(Frame.Text("wsc:message||${decryptedMessage.encryptWithRSA(it.encKey)}"))
                    }
                }
                DONE
            } catch (e: Exception) {
                logger.error("Error processing message: {}", e.message)
                FAIL_NO_PERMISSION // or another suitable error enum
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
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
         * nick : new_nick(optional, null for clear)
         * recovery : same as register without nickname
         * chat : enc_message.prv_target (optional)
         * 
         * - status message -
         * code_gen_successful
         * recovery_success
         * forbidden_nickname
         * register_success
         * login_[complete|fail_no_user|fail_same_user|fail_no_permission]
         * logout_[complete|fail_no_user|fail_no_permission]
         * nick_change_[success|fail_no_user|fail_no_permission]
         * send_[complete|fail_no_permission|fail_unknown]
         * history_fail_login_required
         *
         * - return form -
         * history||time%sender(#)%chat%target(*)|... <encrypted>
         * private_key_send||key
         * message||enc_message
         * message_prv||enc_sender||enc_message
         */

        if (data.startsWith("wsc:")) {
            val currentSession = sessionMap[this] ?: throw NullPointerException("Session is null")

            val rawData = data.split("||")
            val action = rawData[0].replace("wsc:", "")
            val paramRaw = data.replace(rawData[0], "")
            val params = paramRaw.split(".")
            when (action) {
                "code" -> {
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
                        outgoing.send(Frame.Text("wsc:forbidden_nickname"))
                    else {
                        currentSession.registerUser(conType, otpCodeInput, userId, nickName, this)
                        outgoing.send(Frame.Text("wsc:register_success"))
                    }
                }
                "login" -> {
                    val userId = params[0]
                    val conType = ConType.entries[params[1].toIntOrNull() ?: 0]
                    val encryptedUserId = params[2]
                    val loginRes = currentSession.login(userId, conType, encryptedUserId, this)
                    when (loginRes) {
                        DONE -> outgoing.send(Frame.Text("wsc:login_complete"))
                        FAIL_SAME_USER -> outgoing.send(Frame.Text("wsc:login_fail_same_user"))
                        FAIL_NO_USER -> outgoing.send(Frame.Text("wsc:login_fail_no_user"))
                        FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:login_fail_no_permission"))
                    }
                }
                "logout" -> {
                    val loggedInUser = currentSession.loggedInWith
                    val logoutRes = if (loggedInUser != null)
                        currentSession.logout(loggedInUser)
                    else FAIL_NO_USER

                    when (logoutRes) {
                        DONE -> outgoing.send(Frame.Text("wsc:logout_complete"))
                        FAIL_NO_USER -> outgoing.send(Frame.Text("wsc:logout_fail_no_user"))
                        FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:logout_fail_no_permission"))
                        FAIL_SAME_USER -> false
                    }
                }
                "chat" -> {
                    val encryptedMessage = params[0]
                    val prvTarget = params.getOrNull(1)?.ifBlank { null }
                    val recipientUser = prvTarget?.let {
                        val forNicks = WSChat.online.filter { it.nick == prvTarget }
                        val forIds = WSChat.online.filter { it.userId == prvTarget }
                        (forNicks + forIds).ifEmpty { null }
                    } ?: listOf()
                    val messageProcessResult = currentSession.handleEncryptedMessage(encryptedMessage, this, recipientUser)

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
                                    append(time.toLocalDateTime(TimeZone.currentSystemDefault()))
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
        }
        //send(data)
    }
}