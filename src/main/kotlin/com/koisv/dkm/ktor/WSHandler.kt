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
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ExperimentalEncodingApi
@ExperimentalUuidApi
object WSHandler {
    // 설마 어떤 미친놈이 아이디를 이따구로 하겠어?
    const val SERVER_MESSAGE_ID = "==!<[KOI_SERVER_ALERT]>!=="

    val logger: Logger = LogManager.getLogger("Ktor-Server")

    /**
     * 소켓과 연결된 세션을 저장하는 맵
     * 상호 추적 및 연결 상태 확인용
     *
     * Key: WebSocketServerSession
     * Value: ChatSession
     */
    val sessionMap = mutableMapOf<WebSocketServerSession, ChatSession>()

    val chatTimeFormat = DateTimeFormatter.ofPattern("yy-MM-dd E | a hh:mm:ss") ?: throw Exception("Invalid time format")
    val server = WSChat.WSCUser(
        java.time.LocalDateTime.now().toKotlinLocalDateTime(),
        SERVER_MESSAGE_ID, null, "", ConType.PC,
        java.time.LocalDateTime.now().toKotlinLocalDateTime(), null
    )

    /**
     * 서버 알림을 전송합니다.
     *
     * @param message 메시지
     */
    suspend fun serverAlert(message: String) {
        val currentTimestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val withTime = "$message > ${currentTimestamp.toJavaLocalDateTime().format(chatTimeFormat)} <"
        WSChat.online.forEach {
            it.session?.outgoing?.send(
                Frame.Text(
                    buildString {
                        append("wsc:message||")
                        append(SERVER_MESSAGE_ID.encryptWithRSA(it.encKey))
                        append("||")
                        append(withTime.compressEncRSA(it.encKey))
                        append("||")
                        append(currentTimestamp.toString())
                    }
                )
            )
        }

        val msgForm = Triple(currentTimestamp, server, Pair(withTime, null))
        messageHistory.add(msgForm)
        WSChat.saveMsgHistory(msgForm)
    }

    suspend fun statusUpdate() {
        if (WSChat.online.isEmpty()) return
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

    init { if (messageHistory.isEmpty()) messageHistory.addAll(WSChat.loadMsgHistory().reversed()) }

    class RegisterFailedException(): IndexOutOfBoundsException()

    /**
     * 웹소켓 연결에 따른 세션을 나타내는 클래스
     *
     * @property originIP 연결된 클라이언트의 IP 주소
     * @property session 연결된 세션
     */
    class ChatSession(val originIP: String, private val session: WebSocketServerSession) {
        var failCount = 0
        var loggedInWith: WSChat.WSCUser? = null
        val random = Random(Uuid.random().hashCode())
        val otpJob: Job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && session.isActive) {
                delay(30.seconds)
                if (isActive && session.isActive) {
                    if (recoveryCode.isNotEmpty()) recoveryCode = random.nextInt(0, 9999).let {
                        val code = String.format(Locale.KOREAN, "%04d", it)
                        logger.info("RECOVERY RENEWAL | {} | {}", originIP, code)
                        code.hash()
                    }
                    else otpCode = random.nextInt(0, 9999).let {
                        val code = String.format(Locale.KOREAN, "%04d", it)
                        logger.info("OTP RENEWAL | {} | {}", originIP, code)
                        code.hash()
                    }
                }
            }
            otpCode = ""
        }
        var otpCode = ""
        var recoveryCode = ""

        init {
            otpJob.cancel()
        }

        /**
         * 통신 결과를 나타내는 열거형
         *
         * DONE - 성공적으로 처리됨
         * FAIL_SAME_USER - 이미 연결된 사용자가 있어 실패
         * FAIL_NO_USER - 사용자가 존재하지 않아 실패
         * FAIL_NO_PERMISSION - 권한 부족으로 인해 실패
         */
        enum class ConResult { DONE, FAIL_SAME_USER, FAIL_NO_USER, FAIL_NO_PERMISSION }

        /**
         * 사용자의 닉네임을 변경합니다.
         *
         * @param user 사용자
         * @param newNick 새 닉네임 (null 가능)
         */
        suspend fun changeNickname(user: WSChat.WSCUser, newNick: String?): ConResult? {
            val userTargets = WSChat.online.filter { it.userId == user.userId }
            if (userTargets.isEmpty()) return FAIL_NO_USER

            if ((newNick?.length ?: 0) > 32) {
                user.session?.send(Frame.Text("wsc:nick_change_forbidden"))
                return null
            }

            if (newNick == null) {
                logger.info("User {} removed their nickname", user.userId)
                serverAlert("${user.nick ?: user.userId} 님이 닉네임을 삭제했습니다.")
            } else {
                logger.info("User {} changed nickname to {}", user.userId, newNick)
                serverAlert("[${user.userId}] ${user.nick ?: user.userId} 님이 닉네임을 $newNick(으)로 변경했습니다.")
            }

            userTargets.forEach { it.nick = newNick }
            WSChat.online
                .filter { it.userId == user.userId }
                .forEach {
                    it.nick = newNick
                    WSChat.saveWSCUser(it)
                }
            return DONE
        }

        fun rsaKeyCreate(): KeyPair {
            val keygen = KeyPairGenerator.getInstance("rsa")
            keygen.initialize(4096, SecureRandom())
            return keygen.genKeyPair()
        }

        /**
         * 사용자 ID를 암호화된 상태로 받아서 복호화하여 일치하는지 확인하는 함수
         *
         * @param id 사용자 ID
         * @param conType 연결 유형
         * @param encryptedUserId 암호화된 사용자 ID
         * @return 일치 여부
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun keyVerify(id: String, conType: ConType, encryptedUserId: String): Boolean {
            val loginTarget = WSChat.getWSCUser(id).firstOrNull { it.conType == conType }
            if (loginTarget == null) return false

            val decryptedId = encryptedUserId.decryptWithRSA(loginTarget.encKey)

            return decryptedId == id
        }

        /**
         * 사용자 로그인을 처리합니다.
         *
         * @param id 사용자 ID
         * @param conType 연결 유형
         * @param encryptedUserId 암호화된 사용자 ID
         * @param session 세션
         * @return 처리 결과 - ConResult
         */
        suspend fun login(id: String, conType: ConType, encryptedUserId: String, session: WebSocketServerSession): ConResult {
            try {
                val loginTarget = WSChat.getWSCUser(id).firstOrNull { it.conType == conType }
                    ?: return FAIL_NO_USER
                if (WSChat.online.any {it.userId == id && it.conType == conType}) return FAIL_SAME_USER

                return if (keyVerify(id, conType, encryptedUserId)) {
                    logger.info("User {} Logged in at {}", id, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
                    serverAlert("[${conType.name}] ${loginTarget.nick ?: id} 님이 로그인 했습니다.")
                    val newKey = rsaKeyCreate()
                    loginTarget.session = session
                    loginTarget.lastLogin = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    session.outgoing.send(Frame.Text(
                        "wsc:last_login||${loginTarget.lastLogin}${loginTarget.nick?.let{ "||$it" } ?: ""}"
                    ))
                    session.outgoing.send(Frame.Text(
                        "wsc:private_key_send||${id.encryptWithRSA(loginTarget.encKey)}||${loginTarget.conType}||${Base64.encode(newKey.private.encoded).compressEncRSA(loginTarget.encKey)}||1"
                    ))
                    loginTarget.encKey = Base64.encode(newKey.public.encoded)
                    loggedInWith = loginTarget
                    loggedInWith?.let {
                        WSChat.saveWSCUser(it)
                        WSChat.online.add(it)
                    }
                    DONE
                } else FAIL_NO_PERMISSION
            } catch (e: Exception) {
                println("Error processing login: ${e.message}, ${e.stackTraceToString()}")
                return FAIL_NO_PERMISSION
            }
        }

        /**
         * 사용자 로그아웃을 처리합니다.
         *
         * @param user 사용자
         * @return 처리 결과 - ConResult
         */
        suspend fun logout(user: WSChat.WSCUser): ConResult {
            val logoutTarget = WSChat.online.firstOrNull { it.userId == user.userId && it.conType == user.conType }
                ?: return FAIL_NO_USER

            logger.info("User {} Logged out at {}", user.userId, Clock.System.now())
            serverAlert("[${user.conType.name}] ${logoutTarget.nick ?: user.userId} 님이 로그아웃 했습니다.")
            //logoutTarget.session = null
            WSChat.online.remove(logoutTarget)
            loggedInWith = null
            statusUpdate()
            return DONE
        }

        /**
         * 복구 코드를 처리합니다.
         *
         * @param otpCodeInput OTP 코드
         * @param conType 연결 유형
         * @param userId 사용자 ID
         * @param session 세션
         */
        suspend fun handleRecovery(otpCodeInput: String, conType: ConType, userId: String, session: WebSocketServerSession) {
            if (otpCodeInput == recoveryCode) {
                recoveryCode = ""
                otpJob.cancel()
                val userKey = rsaKeyCreate()
                val existingUser = WSChat.getWSCUser(userId).firstOrNull { it.conType == conType }
                    ?: return session.outgoing.send(Frame.Text("wsc:recovery_fail"))
                existingUser.encKey = Base64.encode(userKey.public.encoded)
                session.outgoing.send(Frame.Text(
                    "wsc:private_key_send||${existingUser.userId}||${existingUser.conType}||${Base64.encode(userKey.private.encoded)}||2"
                ))
                serverAlert("[${conType.name}] ${existingUser.nick ?: userId} 님이 로그인 했습니다.")
                WSChat.saveWSCUser(existingUser)
                session.outgoing.send(Frame.Text("wsc:recovery_success"))
            } else session.outgoing.send(Frame.Text("wsc:recovery_fail"))
        }

        /**
         * 사용자 등록을 처리합니다.
         *
         * @param conType 연결 유형
         * @param otpCodeInput OTP 코드
         * @param userId 사용자 ID
         * @param nickName 닉네임
         * @param session 세션
         * @return 처리 결과 - ConResult
         */
        suspend fun registerUser(
            conType: ConType,
            otpCodeInput: String,
            userId: String,
            nickName: String?,
            session: WebSocketServerSession
        ): ConResult {
            return if (otpCodeInput == otpCode) {
                val idRegex = Regex("^[a-zA-Z0-9_-]{2,32}$")

                if (WSChat.getWSCUser(userId).any { it.conType == conType }) {
                    session.outgoing.send(Frame.Text("wsc:register_fail_same_user"))
                    return FAIL_SAME_USER
                }
                if (userId == SERVER_MESSAGE_ID || !idRegex.matches(userId)) {
                    session.outgoing.send(Frame.Text("wsc:register_fail_no_permission"))
                    return FAIL_NO_PERMISSION
                }

                logger.info("User {} Registered at {}", userId, Clock.System.now())
                serverAlert("[${conType.name}] ${nickName ?: userId} 님이 회원가입 했습니다.")
                otpCode = ""
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
                loggedInWith = newUser
                session.outgoing.send(Frame.Text("wsc:last_login||${newUser.lastLogin}${newUser.nick?.let{ "||$it" } ?: ""}"))
                session.outgoing.send(Frame.Text("wsc:private_key_send||${newUser.userId}||${newUser.conType}||${Base64.encode(userKey.private.encoded)}"))
                DONE
            } else FAIL_NO_PERMISSION
        }

        /**
         * 암호화된 메시지를 받아서 처리합니다.
         *
         * @param encryptedMessage 암호화된 메시지
         * @param recipientUser 수신자 ID
         * @return 처리 결과 - ConResult
         */
        suspend fun handleEncryptedMessage(
            encryptedMessage: String,
            recipientUser: String?
        ): ConResult {
            return try {
                val user = loggedInWith ?: return FAIL_NO_PERMISSION

                val decryptedMessage = encryptedMessage.compressDecRSA(user.encKey)
                if (decryptedMessage == null) return FAIL_NO_PERMISSION

                val recvUsers = recipientUser?.let {
                    WSChat.online.filter { user -> user.userId == it }.ifEmpty { listOf() }
                }

                if (recvUsers?.isEmpty() == true) return FAIL_NO_USER

                // Add the message to messageHistory
                val msgForm = Triple(
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                    user,
                    Pair(decryptedMessage, recvUsers?.firstOrNull())
                )

                messageHistory.add(msgForm)
                WSChat.saveMsgHistory(msgForm)

                // Send message to recipientUser only if specified, otherwise to all online users
                if (recvUsers != null) {
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
                    user.session?.outgoing?.send(
                        Frame.Text(
                            buildString {
                                append("wsc:message_prv||")
                                append(recvUsers.first().userId.encryptWithRSA(user.encKey))
                                append("||")
                                append(decryptedMessage.compressEncRSA(user.encKey))
                                append("||")
                                append(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString())
                                append("||s")
                            }
                        )
                    )
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

    /**
     * 기본적인 통신 처리
     * "wsc:$동작||데이터
     *
     * @action [code|recovery|register|login|logout|history|nick|chat]
     * @conType [0:PC|1:모바일]
     *
     * - 데이터 형식 -
     * code : <none> / recovery.user_id (선택)
     * register : otp_code.conType.user_id.nickname(선택)
     * login : user_id.conType.enc_user_id
     * logout : <none>
     * history : <none>
     * nick : new_nick(선택, 빈 문자열 가능)
     * recovery : same as register without nickname
     * chat : enc_message.prv_target (선택)
     *
     * - 상태 코드 -
     * code_gen_successful
     * recovery_[success|failt|fail_no_user|sent]
     * forbidden_nickname
     * register_[success|fail_nickname|fail_same_user|fail_no_permission]
     * login_[success|fail_no_user|fail_same_user|fail_no_permission|fail_blocked]
     * logout_[success|fail_no_user|fail_no_permission]
     * nick_change_[success|fail_no_user|fail_no_permission]
     * send_[complete|fail_no_permission|fail_unknown]
     * history_fail_login_required
     *
     * - 응답 형식 -
     * last_login||time||nickname (선택)|... <암호화됨>
     * history||time%sender(#)%chat%target(*)|... <암호화됨>
     * private_key_send||target_id_enc||conType||key||1 (선택, 1: 로그인, 2: 복구)
     * message||enc_sender||enc_message||time
     * message_prv||enc_sender||enc_message||time
     * status||user_id%onMobile%last_login%nickname (optional)|... <암호화됨>
     *
     * - 압축 형식 -
     * %%sec_pass.data (암호화됨)
     *
     * @param data 수신된 데이터
     */
    suspend fun WebSocketServerSession.handle(data: String) {
        try {
            if (data.startsWith("wsc:")) {
                val currentSession = sessionMap[this] ?: throw NullPointerException("Session is null")

                val rawData = data.split("||")
                val action = rawData[0].replace("wsc:", "")
                val paramRaw = data.replace(rawData[0] + "||", "")
                val params = paramRaw.split(".")
                when (action) {
                    "code" -> {
                        val isRecovery = params.getOrNull(0) == "recovery"
                        val recoveryTarget = params.getOrNull(1)
                        if (isRecovery) {
                            if (recoveryTarget == null) outgoing.send(Frame.Text("wsc:recovery_fail_no_user"))
                            else {
                                currentSession.recoveryCode =
                                    currentSession.random.nextInt(0, 9999).let {
                                        val code = String.format(Locale.KOREAN, "%04d", it)
                                        logger.info("RECOVERY CODE | {} | {}", currentSession.originIP, code)
                                        code.hash()
                                    }
                                outgoing.send(Frame.Text("wsc:recovery_sent"))
                            }
                        } else {
                            currentSession.otpCode =
                                currentSession.random.nextInt(0, 9999).let {
                                    val code = String.format(Locale.KOREAN, "%04d", it)
                                    logger.info("OTP CODE | {} | {}", currentSession.originIP, code)
                                    code.hash()
                                }
                            currentSession.otpJob.start()
                        }
                        outgoing.send(Frame.Text("wsc:code_gen_successful"))
                    }
                    "recovery" -> {
                        val otpCodeInput = params[0]
                        val conType = ConType.entries[params[1].toIntOrNull() ?: 0]
                        val userId = params.getOrNull(2) ?: throw RegisterFailedException()
                        currentSession.handleRecovery(otpCodeInput, conType, userId, this)
                        statusUpdate()
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
                        statusUpdate()
                    }
                    "login" -> {
                        val userId = params[0]
                        val conType = ConType.entries[params[1].toIntOrNull() ?: 0]
                        val encryptedUserId = params[2]
                        val loginRes = currentSession.login(userId, conType, encryptedUserId, this)
                        try {
                            outgoing.trySend(Frame.Text(
                                when (loginRes) {
                                    DONE -> "wsc:login_success"
                                    FAIL_SAME_USER -> "wsc:login_fail_same_user"
                                    FAIL_NO_USER -> "wsc:login_fail_no_user"
                                    FAIL_NO_PERMISSION -> "wsc:login_fail_no_permission"
                                }
                            ))
                        } catch (e: Exception) {
                            logger.error("Error finalizing login: {}\n{}", e.message, e.stackTraceToString())
                        }
                        if (loginRes != DONE)
                            if (currentSession.failCount > 5) {
                                outgoing.send(Frame.Text("wsc:login_fail_blocked"))
                                close(CloseReason(
                                    CloseReason.Codes.VIOLATED_POLICY,
                                    "Too many failed login attempts"
                                ))
                            }
                        else currentSession.failCount = 0
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
                        statusUpdate()
                        flush()
                    }
                    "chat" -> {
                        val key = currentSession.loggedInWith?.encKey
                        val encryptedMessage = params[0]
                        val prvTarget = params.getOrNull(1)?.ifBlank { null }
                        val messageProcessResult =
                            if (key == null) FAIL_NO_PERMISSION
                            else currentSession.handleEncryptedMessage(encryptedMessage, prvTarget?.decryptWithRSA(key))

                        when (messageProcessResult) {
                            DONE -> outgoing.send(Frame.Text("wsc:send_success"))
                            FAIL_NO_USER -> outgoing.send(Frame.Text("wsc:send_fail_no_user"))
                            FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:send_fail_no_permission"))
                            else -> outgoing.send(Frame.Text("wsc:send_fail_unknown"))
                        }
                        statusUpdate()
                    }
                    "history" -> {
                        val loggedInUser = currentSession.loggedInWith
                        if (loggedInUser == null) {
                            outgoing.send(Frame.Text("wsc:history_fail_login_required"))
                        } else {

                            val delimiter = "|"  // Choose a unique delimiter not present in Base64
                            val lastFiftyMessages = messageHistory.takeLast(50)

                            val formattedMessages = lastFiftyMessages.joinToString(delimiter) { (time, sender, content) ->
                                buildString {
                                    append(time
                                        .toString().encryptWithRSA(loggedInUser.encKey))
                                    append("%")
                                    append((sender?.nick ?: sender?.userId ?: "<삭제된 사용자>")
                                        .encryptWithRSA(loggedInUser.encKey))
                                    append("%")
                                    append(content.first
                                        .encryptWithRSA(loggedInUser.encKey))
                                    if (
                                        content.second != null &&
                                        (content.second?.userId == loggedInUser.userId || sender?.userId == loggedInUser.userId)
                                    ) {
                                        append("%")
                                        append((content.second?.nick ?: content.second?.userId ?: "<삭제된 사용자>"
                                                ).encryptWithRSA(loggedInUser.encKey))
                                    }
                                }
                            }.ifEmpty { "empty" }.compressEncRSA(loggedInUser.encKey)
                            outgoing.send(Frame.Text("wsc:history||$formattedMessages"))
                        }
                        statusUpdate()
                    }
                    "nick" -> {
                        val loggedInUser = currentSession.loggedInWith
                        if (loggedInUser == null) {
                            outgoing.send(Frame.Text("wsc:nick_change_fail_no_permission"))
                        } else {
                            val newNick = paramRaw.ifBlank { null }?.decryptWithRSA(loggedInUser.encKey)
                            val nickChangeResult = currentSession.changeNickname(loggedInUser, newNick)

                            when (nickChangeResult) {
                                DONE -> outgoing.send(Frame.Text("wsc:nick_change_success"))
                                FAIL_NO_USER -> outgoing.send(Frame.Text("wsc:nick_change_fail_no_user"))
                                FAIL_NO_PERMISSION -> outgoing.send(Frame.Text("wsc:nick_change_fail_no_permission"))
                                FAIL_SAME_USER -> false
                                else -> false
                            }
                            statusUpdate()
                        }
                    }
                }
            }
            incoming.consumeAsFlow()
        } catch (_: Exception) {
            logger.info("Transmission Stopped from {}", sessionMap[this]?.originIP)
            // 이거 안하면 나중에 채널 끊길때마다 정신 못차립니다 요놈.
        }
        //send(data)
    }
}