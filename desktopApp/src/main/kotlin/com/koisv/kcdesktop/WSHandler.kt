package com.koisv.kcdesktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.koisv.kcdesktop.Tools.compressDecRSA
import com.koisv.kcdesktop.Tools.compressEncRSA
import com.koisv.kcdesktop.Tools.decryptWithRSA
import com.koisv.kcdesktop.Tools.encryptWithRSA
import com.koisv.kcdesktop.Tools.hash
import com.koisv.kcdesktop.Tools.toPrvKey
import com.koisv.kcdesktop.ui.ChatUI
import com.koisv.kcdesktop.ui.MainUI
import com.koisv.kcdesktop.ui.MainUI.progressText
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.consumeAsFlow
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.security.PrivateKey
import java.time.LocalDateTime
import javax.naming.AuthenticationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object WSHandler {
    // 설마 어떤 미친놈이 아이디를 이따구로 하겠어?
    const val SERVER_MESSAGE_ID = "==!<[KOI_SERVER_ALERT]>!=="
    val logger: Logger = LogManager.getLogger()

    data class WSCUser(
        val id: String,
        val nickname: String? = id,
        val lastOnline: LocalDateTime,
        val onMobile: Boolean
    )

    lateinit var wsSession: ClientWebSocketSession
    lateinit var myKeyFile: File

    var keyUpdated = false
    var sessionFailed by mutableStateOf(false)
    var autoLogin by mutableStateOf(false)
    var autoLoginId by mutableStateOf("")
    val responses = mutableMapOf<String, Any>()
    var loggedInWith: WSCUser? = null
    var myKey: PrivateKey? = null

    val sessionOpened get() = ::wsSession.isInitialized && wsSession.isActive
    val loggedIn get() = loggedInWith != null
    val loggedInState by mutableStateOf(loggedIn)
    val wsClient = HttpClient(CIO) {
        install(WebSockets) {
            this.maxFrameSize = Long.MAX_VALUE
            this.pingIntervalMillis = 10000
        }
    }
    val sessionKeyFolder = File("./sessionKeys")
    val messages = mutableStateListOf<Triple<String, LocalDateTime, Pair<String, String?>>>()
    val onlines = mutableStateListOf<WSCUser>()

    fun getKeys(): List<File> {
        if (!sessionKeyFolder.exists()) return emptyList()
        else sessionKeyFolder.listFiles()?.let { files ->
            return files
                .filter { it.isFile && it.extension == "pem" }
        } ?: return emptyList()
    }

    /**
     * 서버에 OTP 요청을 보냅니다.
     *
     * @return 성공 여부
     */
    suspend fun requestRecovery(id: String): Boolean {
        if (!sessionOpened) return false
        wsSession.send(Frame.Text("wsc:code||recovery.$id"))

        return withTimeoutOrNull(5000) {
            while (!responses.containsKey("recovery_request") && isActive) { 0 }
            try { responses.remove("recovery_request") as? Boolean }
            catch (_: Exception) { false }
        } == true
    }

    /**
     * 서버에 OTP 요청을 보냅니다.
     *
     * @return 성공 여부
     */
    suspend fun otpRequest(): Boolean {
        if (!sessionOpened) return false
        wsSession.send(Frame.Text("wsc:code"))
        return withTimeoutOrNull(5000) {
            while (!responses.containsKey("otp") && isActive) { 0 }
            try { responses.remove("otp") as? Boolean }
            catch (_: Exception) { false }
        } == true
    }

    /**
     * 서버에 복구 요청을 보냅니다.
     *
     * @param id 복구할 아이디
     * @param otp OTP 코드
     * @return 성공 여부
     */
    suspend fun sendRecovery(id: String, otp: String): Boolean {
        if (!sessionOpened) return false
        wsSession.send(Frame.Text("wsc:recovery||${otp.hash()}.0.$id"))

        val response = withTimeoutOrNull(5000) {
            while (!responses.containsKey("recovery") && isActive) { 0 }
            try { responses.remove("recovery") as? Boolean } catch (_: Exception) { false }
        } == true

        return if (response) {
            myKey = responses.remove("key_recovery") as? PrivateKey ?: return false
            keyUpdated = true
            sendLogin(id, myKeyFile) == 0
        } else false
    }

    /**
     * 서버에 회원가입 요청을 보냅니다.
     *
     * @param id 회원가입할 아이디
     * @param nickname 회원가입할 닉네임 (null일 경우 닉네임 없음)
     * @param otp OTP 코드
     * @return 성공 여부 - 0: 성공, 1: 사용자 없음, 2: 같은 사용자, 3: 권한 없음, 4: 시간 초과
     */
    suspend fun sendRegister(id: String, nickname: String?, otp: String): Short = coroutineScope {
        if (!sessionOpened) return@coroutineScope 99
        val response = withTimeoutOrNull(5000) {
            wsSession.send(Frame.Text("wsc:register||${otp.hash()}.0.$id${nickname?.let { ".$it" } ?: ""}"))
            while (!responses.containsKey("register") && isActive) { 0 }
            try { responses.remove("register") as? Int }
            catch (_: Exception) { false }
        }

        when (response) {
            0 -> {
                myKey = responses.remove("key") as? PrivateKey ?: return@coroutineScope 2
                loggedInWith = WSCUser(id, nickname, LocalDateTime.now(), onMobile = false)
                wsSession.send(Frame.Text("wsc:history"))
                0
            }
            1 -> 3
            2 -> 4
            3 -> 3
            else -> 1
        }
    }

    /**
     * 서버에 로그인 요청을 보냅니다.
     *
     * @param id 로그인할 아이디
     * @param key 로그인할 키 파일
     * @return 성공 여부 - 0: 성공, 1: 사용자 없음, 2: 같은 사용자, 3: 권한 없음, 4: 시간 초과, 5: 오류, 6: 키 파일 오류
     */
    suspend fun sendLogin(id: String, key: File): Int = coroutineScope {
        if (!sessionOpened) return@coroutineScope 5
        myKeyFile = key
        if (!keyUpdated) myKey = myKeyFile.readBytes().toPrvKey() ?: return@coroutineScope 6
        val response = withTimeoutOrNull(5000) {
            wsSession.send(Frame.Text("wsc:login||$id.0.${id.encryptWithRSA(key.readBytes().toPrvKey() ?: return@withTimeoutOrNull 6)}"))
            while (!responses.containsKey("login") && isActive) { 0 }
            try { responses.remove("login") as? Int }
            catch (e: Exception) {
                e.printStackTrace()
                5 }
        } ?: 4

        return@coroutineScope if (response == 0) {
            myKey = responses.remove("key_login") as? PrivateKey ?: return@coroutineScope 1
            keyUpdated = true
            0
        } else {
            if (!keyUpdated) myKey = null
            response
        }
    }

    /**
     * 서버에 로그아웃 요청을 보냅니다.
     *
     * @return 성공 여부 (세션 없음: false)
     */
    suspend fun sendLogout(): Boolean {
        if (!sessionOpened) return false
        wsSession.send(Frame.Text("wsc:logout"))
        return true
    }

    /**
     * 서버에 메시지를 보냅니다.
     *
     * @param message 보낼 메시지
     * @param receiver 받는이 (null일 경우 전체에게)
     * @return 성공 여부 (세션 없음: false)
     */
    suspend fun sendMessage(message: String, receiver: String? = null): Boolean {
        if (!sessionOpened) return false
        val key = myKey ?: return false
        val messageData = buildString {
            append("wsc:chat||")
            append(message.compressEncRSA(key))
            receiver?.let{ append("." + it.encryptWithRSA(key)) }
        }

        wsSession.send(Frame.Text(messageData))
        return true
    }

    /**
     * 서버에 닉네임 변경 요청을 보냅니다.
     *
     * @param newNick 변경할 닉네임 (null일 경우 닉네임 삭제)
     * @return 0: 성공, 1: 세션 없음, 2: 키 없음, 3: 오류, 4: 시간 초과, 5: 금지된 닉네임
     */
    suspend fun sendNickChange(newNick: String?): Int {
        if (!sessionOpened) return 1
        val key = myKey ?: return 2
        val messageData = buildString {
            append("wsc:nick||")
            append(newNick?.encryptWithRSA(key) ?: "")
        }
        wsSession.send(Frame.Text(messageData))

        return withTimeoutOrNull(5000) {
            while (!responses.containsKey("nick") && isActive) { 0 }
            try { responses.remove("nick") as? Int }
            catch (_: Exception) { 3 }
        } ?: 4
    }

    init {
        sessionKeyFolder.mkdirs()
        startWS().start()
    }

    /**
     * 웹소켓 세션을 시작합니다.
     *
     * @return 웹소켓 Job
     */
    fun startWS() = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        try {
            wsClient.use { wsClient ->
                if (wsDebug) wsClient.ws(host = "localhost", path = "/", port = 8921) {
                    wsSession = this
                    sessionFailed = false
                    try { channelHandler(incoming, outgoing) }
                    catch (e: Exception) { logger.error("Failed to handle channel! - {}", e.stackTraceToString()) }
                    logger.debug("Disconnected from server!")
                    sessionFailed = true
                    MainUI.disconnectedUI = true
                }
                else wsClient.wss(host = "ws.koisv.com", path = "/", port = 443) {
                    wsSession = this
                    sessionFailed = false
                    channelHandler(incoming, outgoing)
                    logger.debug("Disconnected from server!")
                    sessionFailed = true
                    MainUI.disconnectedUI = true
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to server! - {}", e.stackTraceToString())
            sessionFailed = true
        }
    }

    /**
     * 웹소켓 채널 핸들러입니다.
     *
     * wsc:로 시작하는 데이터를 처리합니다.
     * ||로 구분된 데이터를 처리합니다.
     * 로그인 이후 데이터는 모두 암호화된 데이터로 처리합니다.
     * 후행 데이터가 없을 경우 상태 데이터로 처리합니다.
     *
     * 데이터 수신 형태:
     * - wsc:status||사용자 목록
     * - wsc:last_login||마지막 로그인 시간
     * - wsc:private_key_send||사용자명||디바이스||암호화된 키||용도 파라미터 (1: 로그인, 2: 복구)]
     * - wsc:message||보낸이||메시지||시간
     * - wsc:message_prv||보낸이||메시지||시간||받는이
     * - wsc:history||메시지 목록
     *
     * 상태 데이터 수신 형태:
     * - code_gen_successful
     * - register_[success|fail_same_user|fail_nickname|fail_no_permission]
     * - login_[success|fail_no_user|fail_same_user|fail_no_permission]
     * - recovery_[sent|success|fail|fail_no_user]
     *
     * @param incoming 수신 채널
     * @param outgoing 송신 채널
     */
    suspend fun channelHandler(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) {
        try {
            for (frame in incoming) {
                try {
                    when (frame) {
                        is Frame.Text -> {
                            val rawData = frame.readText()
                            if (rawData.startsWith("wsc:")) {
                                if (rawData.contains("||")) {
                                    val data = rawData.split("||")
                                    val action = data[0].replace("wsc:", "")
                                    val key = myKey
                                    when (action) {
                                        "status" -> {
                                            while (!keyUpdated && !loggedIn) { 0 }
                                            logger.debug("status received")
                                            logger.debug("key: {}", key)
                                            if (key == null) return
                                            val decoded = data[1].compressDecRSA(key)
                                                ?: throw AuthenticationException("Failed to decrypt status")
                                            val rawUsers = decoded.split("|")
                                            val result = mutableListOf<WSCUser>()
                                            rawUsers.forEach { user ->
                                                val userData = user.split("%")
                                                val id = userData[0]
                                                val onMobile = userData[1] == "1"
                                                val lastOnline = LocalDateTime.parse(userData[2])
                                                val nickname = userData.getOrNull(3)
                                                result.add(WSCUser(id, nickname, lastOnline, onMobile))
                                            }
                                            if (onlines.isNotEmpty()) {
                                                onlines.clear()
                                                onlines.addAll(result)
                                                onlines.addFirst(onlines.removeAt(onlines.indexOfFirst { it.id == loggedInWith?.id }))
                                            }
                                        }

                                        "last_login" -> {
                                            val lastOnline = data[1]
                                            val nickname = data.getOrNull(2)
                                            loggedInWith = WSCUser(
                                                MainUI.idInput, nickname,
                                                LocalDateTime.parse(lastOnline), onMobile = false
                                            )
                                            loggedInWith?.let { onlines.add(it) }
                                        }

                                        "private_key_send" -> {
                                            progressText = "키 발급 중..."
                                            val isLogin = data.getOrNull(4) == "1"
                                            val isRecovery = data.getOrNull(4) == "2"
                                            if (isLogin) {
                                                logger.debug("private key received")
                                                logger.debug("key: {}", key)
                                                if (key == null) return
                                                if (data[1].decryptWithRSA(key) != MainUI.idInput || data[2] != "PC") return
                                                myKey = myKeyFile.readBytes().toPrvKey()
                                                try {
                                                    myKeyFile.delete()
                                                } catch (_: Exception) {
                                                }
                                                val newKey = data[3].compressDecRSA(key)
                                                    ?: throw AuthenticationException("Failed to decrypt key")
                                                myKeyFile.createNewFile()
                                                myKeyFile.writeBytes(Base64.decode(newKey))
                                                responses["key_login"] = newKey.toPrvKey()
                                            } else {
                                                if (data[1] != MainUI.idInput || data[2] != "PC") return
                                                var keyNum = 0
                                                while (File("./sessionKeys/key$keyNum.pem").exists()) keyNum++
                                                val newKey = File("./sessionKeys/key$keyNum.pem")

                                                myKeyFile = newKey
                                                newKey.createNewFile()
                                                newKey.writeBytes(Base64.decode(data[3]))
                                                responses[if (isRecovery) "key_recovery" else "key"] =
                                                    data[3].toPrvKey()
                                            }
                                        }

                                        "message" -> {
                                            if (loggedIn && key != null) {
                                                try {
                                                    val (sender, timestamp, message) =
                                                        Triple(
                                                            data[1].decryptWithRSA(key)
                                                                ?: throw AuthenticationException("Failed to decrypt message"),
                                                            LocalDateTime.parse(data[3]),
                                                            data[2].compressDecRSA(key)
                                                                ?: throw AuthenticationException("Failed to decrypt message")
                                                        )
                                                    messages.add(Triple(sender, timestamp, Pair(message, null)))
                                                } catch (e: Exception) {
                                                    logger.fatal(
                                                        "Failed to decrypt message! - {}",
                                                        e.stackTraceToString()
                                                    )
                                                }
                                            }
                                        }

                                        "message_prv" -> {
                                            if (loggedIn && key != null) {
                                                val isMyself = data.getOrNull(4) == "s"
                                                val (senderId, timestamp, message) =
                                                    Triple(
                                                        data[1].decryptWithRSA(key)
                                                            ?: throw AuthenticationException("Failed to decrypt message"),
                                                        LocalDateTime.parse(data[3]),
                                                        data[2].compressDecRSA(key)
                                                            ?: throw AuthenticationException("Failed to decrypt message")
                                                    )
                                                val sender =
                                                    onlines.firstOrNull { it.id == senderId }?.nickname ?: senderId
                                                val myself =
                                                    (loggedInWith?.nickname ?: loggedInWith?.id) ?: "<확인되지 않은 나 자신>"
                                                messages.add(
                                                    Triple(
                                                        if (isMyself) myself else sender,
                                                        timestamp, Pair(message, if (isMyself) sender else myself)
                                                    )
                                                )
                                            }
                                        }

                                        "history" -> {
                                            if (loggedIn && key != null) {
                                                val history = data[1].compressDecRSA(key)
                                                    ?: throw AuthenticationException("Failed to decrypt history")
                                                try {
                                                    ChatUI.historyLoading = true
                                                    val messages = history.split("|")
                                                    messages.forEach {
                                                        val message = it.split("%")
                                                        val sender = message[1].decryptWithRSA(key)
                                                            ?: throw AuthenticationException("Failed to decrypt history")
                                                        val timestamp = LocalDateTime.parse(
                                                            message[0].decryptWithRSA(key)
                                                                ?: throw AuthenticationException("Failed to decrypt history")
                                                        )
                                                        val content = message[2].decryptWithRSA(key)
                                                            ?: throw AuthenticationException("Failed to decrypt history")
                                                        val prvTarget = message.getOrNull(3)?.decryptWithRSA(key)
                                                        this.messages.add(
                                                            Triple(
                                                                sender, timestamp,
                                                                Pair(content, prvTarget)
                                                            )
                                                        )
                                                    }
                                                    ChatUI.historyLoading = false
                                                } catch (e: Exception) {
                                                    logger.fatal(
                                                        "Failed to decrypt history - {}",
                                                        e.stackTraceToString()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val status = rawData.replace("wsc:", "")
                                    when (status) {
                                        "test" -> println("test")
                                        "code_gen_successful" -> responses["otp"] = true
                                        "register_success" -> responses["register"] = 0
                                        "register_fail_same_user" -> responses["register"] = 1
                                        "register_fail_nickname" -> responses["register"] = 2
                                        "register_fail_no_permission" -> responses["register"] = 3
                                        "login_success" -> responses["login"] = 0
                                        "login_fail_no_user" -> responses["login"] = 1
                                        "login_fail_same_user" -> responses["login"] = 2
                                        "login_fail_no_permission" -> responses["login"] = 3
                                        "recovery_sent" -> responses["recovery_request"] = true
                                        "recovery_success" -> responses["recovery"] = true
                                        "recovery_fail" -> responses["recovery"] = false
                                        "recovery_fail_no_user" -> responses["recovery_request"] = false
                                        "nick_change_success" -> responses["nick"] = 0
                                        "nick_change_fail_no_permission" -> responses["nick"] = 3
                                        "nick_change_fail_no_user" -> responses["nick"] = 3
                                        "nick_change_forbidden" -> responses["nick"] = 5
                                    }
                                }
                            }
                            incoming.consumeAsFlow()
                        }

                        is Frame.Close -> {
                            logger.debug("stopped")
                            outgoing.send(Frame.Close())
                        }

                        else -> {
                            logger.debug("Not Yet Implementedq")
                            outgoing.send(Frame.Text("Not Yet Implemented"))
                        }
                    }
                } catch (e: Exception) {
                    println("error")
                    logger.error("Failed to incoming frame! - {}", e.stackTraceToString())
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to handle incoming frame! - {}", e.stackTraceToString())
        }
    }
}