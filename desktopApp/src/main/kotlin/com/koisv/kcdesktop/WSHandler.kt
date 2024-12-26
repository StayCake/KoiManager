package com.koisv.kcdesktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.koisv.kcdesktop.ui.MainUI
import com.koisv.kcdesktop.ui.MainUI.progressText
import com.koisv.kcdesktop.ui.Tools.compressDecRSA
import com.koisv.kcdesktop.ui.Tools.compressEncRSA
import com.koisv.kcdesktop.ui.Tools.decryptWithRSA
import com.koisv.kcdesktop.ui.Tools.encryptWithRSA
import com.koisv.kcdesktop.ui.Tools.toPrvKey
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PrivateKey
import java.time.LocalDateTime
import javax.naming.AuthenticationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
@OptIn(DelicateCoroutinesApi::class)
object WSHandler {
    data class WSCUser(
        val id: String,
        val nickname: String? = id,
        val lastOnline: LocalDateTime,
        val onMobile: Boolean
    )

    lateinit var wsSession: ClientWebSocketSession
    lateinit var myKeyFile: File

    var keyUpdated = false
    var sessionFailed = false
    var autoLogin by mutableStateOf(false)
    var autoLoginId by mutableStateOf("")
    val responses = mutableMapOf<String, Any>()
    var loggedInWith: WSCUser? = null
    var myKey: PrivateKey? = null

    val sessionOpened get() = ::wsSession.isInitialized && wsSession.isActive
    val loggedIn get() = loggedInWith != null
    val loggedInState by mutableStateOf(loggedIn)
    val wsClient = HttpClient(CIO) { install(WebSockets) }
    val sessionKeyFolder = File("./sessionKeys")
    val messages =
        mutableStateListOf<Triple<String, LocalDateTime, Pair<String, String?>>>()
    val onlines = mutableStateListOf<WSCUser>()

    fun getKeys(): List<File> {
        if (!sessionKeyFolder.exists()) return emptyList()
        else sessionKeyFolder.listFiles()?.let { files ->
            return files
                .filter { it.isFile && it.extension == "pem" }
        } ?: return emptyList()
    }

    suspend fun otpRequest(): Boolean {
        if (!sessionOpened) return false
        wsSession.send(Frame.Text("wsc:code"))
        return withTimeoutOrNull(5000) {
            while (!responses.containsKey("otp") && isActive) { 0 }
            try { responses.remove("otp") as? Boolean }
            catch (_: Exception) { false }
        } == true
    }

    // 0: Success, 1: No user, 2: Same user, 3: No permission, 4: Timeout
    suspend fun sendRegister(id: String, nickname: String?, otp: String): Short = coroutineScope {
        if (!sessionOpened) return@coroutineScope 99
        println("Sending register request")
        val response = withTimeoutOrNull(5000) {
            wsSession.send(Frame.Text("wsc:register||${otp.hash()}.0.$id${nickname?.let { ".$it" } ?: ""}"))
            println("Sent register request")
            while (!responses.containsKey("register") && isActive) { 0 }
            try { responses.remove("register") as? Int }
            catch (_: Exception) { false }
        }

        when (response) {
            0 -> {
                myKey = responses.remove("key") as? PrivateKey ?: return@coroutineScope 2
                loggedInWith = WSCUser(id, nickname, LocalDateTime.now(), onMobile = false)
                0
            }
            1 -> 3
            2 -> 4
            3 -> 3
            else -> 1
        }
    }

    // 0: Success, 1: No user, 2: Same user, 3: No permission, 4: Timeout, 5: Exception
    suspend fun sendLogin(id: String, key: File): Int = coroutineScope {
        println("Login: $id, $sessionOpened, $sessionFailed")
        if (!sessionOpened) return@coroutineScope 5
        myKeyFile = key
        myKey = myKeyFile.readBytes().toPrvKey()
        println("Sending login request")
        val response = withTimeoutOrNull(5000) {
            wsSession.send(Frame.Text("wsc:login||$id.0.${id.encryptWithRSA(key.readBytes().toPrvKey())}"))
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

    suspend fun sendLogout(): Boolean {
        if (!sessionOpened) return false
        wsSession.send(Frame.Text("wsc:logout"))
        return true
    }

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

    init {
        sessionKeyFolder.mkdirs()
        startWS().start()
    }

    fun startWS() = GlobalScope.launch {
        try {
            if (wsDebug) wsClient.ws(host = "localhost", path = "/", port = 8921) {
                wsSession = this
                sessionFailed = false
                channelHandler(incoming, outgoing)
                sessionFailed = true
                MainUI.disconnectedUI = true
            }
            else wsClient.wss(host = "ws.koisv.com", path = "/", port = 443) {
                wsSession = this
                sessionFailed = false
                channelHandler(incoming, outgoing)
                sessionFailed = true
                MainUI.disconnectedUI = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to connect to server!")
            sessionFailed = true
        }
    }

    suspend fun channelHandler(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) {
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    val rawData = frame.readText()
                    if (rawData.startsWith("wsc:")) {
                        if (rawData.contains("||")) {
                            val data = rawData.split("||")
                            val action = data[0].replace("wsc:", "")
                            println("Action: $action")
                            println("Data: ${data.joinToString("\n")}")
                            val key = myKey
                            when (action) {
                                "status" -> {
                                    while (!keyUpdated) { 0 }
                                    if (key == null) return
                                    val decoded = data[1].compressDecRSA(key) ?:
                                    throw AuthenticationException("Failed to decrypt status")
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
                                    onlines.clear()
                                    onlines.addAll(result)
                                    onlines.addFirst(onlines.removeAt(onlines.indexOfFirst { it.id == loggedInWith?.id }))
                                }
                                "last_login" -> {
                                    val lastOnline = data[1]
                                    val nickname = data.getOrNull(2)
                                    loggedInWith = WSCUser(
                                        MainUI.id, nickname,
                                        LocalDateTime.parse(lastOnline), onMobile = false
                                    )
                                    loggedInWith?.let { onlines.add(it) }
                                }
                                "private_key_send" -> {
                                    progressText = "키 발급 중..."
                                    if (data.getOrNull(2) == "1") {
                                        if (key == null) return
                                        myKey = myKeyFile.readBytes().toPrvKey()
                                        try { myKeyFile.delete() } catch (_: Exception) { }
                                        val key = data[1].compressDecRSA(key) ?:
                                        throw AuthenticationException("Failed to decrypt key")
                                        println("Key: $key")
                                        myKeyFile.createNewFile()
                                        myKeyFile.writeBytes(Base64.decode(key))
                                        responses["key_login"] = key.toPrvKey()
                                    } else {
                                        var keyNum = 0
                                        while (File("./sessionKeys/key$keyNum.pem").exists()) keyNum++
                                        val newKey = File("./sessionKeys/key$keyNum.pem")

                                        myKeyFile = newKey
                                        newKey.createNewFile()
                                        newKey.writeBytes(Base64.decode(data[1]))
                                        responses["key"] = data[1].toPrvKey()
                                    }
                                }
                                "message" -> {
                                    if (loggedIn && key != null) {
                                        println("Message: ${data[2]}")
                                        try {
                                            val (sender, timestamp, message) =
                                                Triple(
                                                    data[1].decryptWithRSA(key) ?: throw AuthenticationException("Failed to decrypt message"),
                                                    LocalDateTime.parse(data[3]),
                                                    data[2].compressDecRSA(key) ?: throw AuthenticationException("Failed to decrypt message")
                                                )
                                            println("Message: $message")
                                            messages.add(Triple(sender, timestamp, Pair(message, null)))
                                        } catch (e: Exception) {
                                            println("Failed to decrypt message")
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                "message_prv" -> {
                                    if (loggedIn && key != null) {
                                        val (sender, timestamp, message) =
                                            Triple(
                                                data[1].decryptWithRSA(key) ?: throw AuthenticationException("Failed to decrypt message"),
                                                LocalDateTime.parse(data[3]),
                                                data[2].compressDecRSA(key) ?: throw AuthenticationException("Failed to decrypt message")
                                            )
                                        messages.add(Triple(sender, timestamp, Pair(message, loggedInWith?.nickname)))
                                    }
                                }
                            }
                        } else {
                            val status = rawData.replace("wsc:", "")
                            println("Status: $status")
                            when (status) {
                                "code_gen_successful" -> responses["otp"] = true
                                "register_success" -> responses["register"] = 0
                                "register_fail_same_user" -> responses["register"] = 1
                                "register_fail_nickname" -> responses["register"] = 2
                                "register_fail_no_permission" -> responses["register"] = 3
                                "login_success" -> responses["login"] = 0
                                "login_fail_no_user" -> responses["login"] = 1
                                "login_fail_same_user" -> responses["login"] = 2
                                "login_fail_no_permission" -> responses["login"] = 3
                            }
                        }
                    }
                }
                is Frame.Close -> {
                    outgoing.send(Frame.Close())
                }
                else -> {
                    outgoing.send(Frame.Text("Not Yet Implemented"))
                }
            }
        }
    }

    private fun String.hash(): String {
        val md = MessageDigest.getInstance("SHA-512")
        val messageDigest = md.digest(toByteArray())

        val no = BigInteger(1, messageDigest)
        var hashText = no.toString(16)
        while (hashText.length < 32) hashText = "0$hashText"

        return hashText
    }
}