package com.koisv.kcdesktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.koisv.kcdesktop.ui.MainUI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Image
import java.io.FileNotFoundException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@ExperimentalEncodingApi
object Tools {
    fun String.hash(): String {
        val md = MessageDigest.getInstance("SHA-512")
        val messageDigest = md.digest(toByteArray())

        val no = BigInteger(1, messageDigest)
        var hashText = no.toString(16)
        while (hashText.length < 32) hashText = "0$hashText"

        return hashText
    }

    fun searchUsers(searchFor: String): List<WSHandler.WSCUser?> =
        WSHandler.onlines.filter { it.id.contains(searchFor) || it.nickname?.contains(searchFor) == true }
            .sortedBy { searchFor }

    fun getKeys() =
        if (WSHandler.sessionKeyFolder.exists() && WSHandler.sessionKeyFolder.listFiles().isNotEmpty()) {
            if (WSHandler.sessionKeyFolder.listFiles().size > 5) {
                MainUI.keyFileExceed = true
                WSHandler.getKeys().take(5)
            }
            else WSHandler.getKeys()
        } else {
            MainUI.isRegister = true
            MainUI.loginAlert = false
            emptyList()
        }

    @DelicateCoroutinesApi
    suspend fun getConnected(): Boolean = coroutineScope {
        if (WSHandler.sessionFailed) WSHandler.startWS().start()
        return@coroutineScope withTimeoutOrNull(3.seconds) {
            while (!WSHandler.sessionOpened && WSHandler.sessionFailed && isActive) { 0 }
            WSHandler.logger.debug("so: ${WSHandler.sessionOpened}, sf: ${WSHandler.sessionFailed}")
            WSHandler.sessionOpened && !WSHandler.sessionFailed
        } == true
    }

    @Composable
    internal fun painterResource(
        resourcePath: String
    ): Painter = when (resourcePath.substringAfterLast(".")) {
        else -> rememberBitmapResource(resourcePath)
    }

    @Composable
    internal fun rememberBitmapResource(path: String): Painter {
        return remember(path) {
            BitmapPainter(
                Image.Companion.makeFromEncoded(readResourceBytes(path)).toComposeImageBitmap()
            )
        }
    }

    private object ResourceLoader
    private fun readResourceBytes(resourcePath: String) =
        ResourceLoader.javaClass.classLoader.getResourceAsStream(resourcePath)?.readAllBytes()
            ?: throw FileNotFoundException()

    val chatTimeFormat =
        DateTimeFormatter.ofPattern("yy-MM-dd E | a hh:mm:ss") ?: throw Exception("Invalid time format")

    fun String.toPrvKey(): PrivateKey {
        val rawKey = Base64.Default.decode(this)
        val spec = PKCS8EncodedKeySpec(rawKey)
        val fact = KeyFactory.getInstance("RSA")
        return fact.generatePrivate(spec)
    }

    fun ByteArray.toPrvKey(): PrivateKey? {
        try {
            val spec = PKCS8EncodedKeySpec(this)
            val fact = KeyFactory.getInstance("RSA")
            return fact.generatePrivate(spec)
        } catch (_: Exception) { return null }
    }

    fun String.decryptWithRSA(prvKey: PrivateKey): String? {
        try {
            val cipher = Cipher.getInstance("RSA")
            cipher.init(Cipher.DECRYPT_MODE, prvKey)
            return String(cipher.doFinal(Base64.Default.decode(this)))
        } catch (_: BadPaddingException) {
            return null
        }
    }

    fun String.encryptWithRSA(prvKey: PrivateKey): String? {
        try {
            val cipher = Cipher.getInstance("RSA")
            cipher.init(Cipher.ENCRYPT_MODE, prvKey)
            return Base64.Default.encode(cipher.doFinal(this.toByteArray()))
        } catch (_: BadPaddingException) {
            return null
        }
    }

    fun String.compressEncRSA(prvKey: PrivateKey): String? {
        val password = passcodeGen(16)
        val (secretKeySpec, gcmParamSpec) = getSpec(password)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParamSpec)

        val encryptedValue = cipher.doFinal(this.toByteArray())

        return ("%%${password.encryptWithRSA(prvKey)}~" + Base64.Default.encode(encryptedValue))
    }

    fun String.compressDecRSA(prvKey: PrivateKey): String? {
        if (!this.startsWith("%%")) return null
        val data = this.substringAfter('~')
        val password = this.substringAfter("%%").substringBefore('~')
            .decryptWithRSA(prvKey) ?: return null

        val (secretKeySpec, gcmParamSpec) = getSpec(password)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParamSpec)

        val decryptedByteValue = cipher.doFinal(Base64.Default.decode(data))
        return String(decryptedByteValue)
    }

    fun getSpec(password: String): Pair<SecretKeySpec, GCMParameterSpec> {
        val secretKeySpec = SecretKeySpec(password.toByteArray(), "AES")
        val iv = ByteArray(16)
        val charArray = password.toCharArray()
        for (i in 0 until charArray.size) {
            iv[i] = charArray[i].code.toByte()
        }
        val gcmParamSpec = GCMParameterSpec(128, iv)
        return Pair(secretKeySpec, gcmParamSpec)
    }

    fun passcodeGen(len: Int): String {
        val alphabet: List<Char> = ('가'..'힣').toList()
        val pre = buildString { repeat((len-1)/3) { append(alphabet.random()) } }
        val rand = Random(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
        return pre.toMutableList()
            .apply { add(rand.nextInt(0, pre.length + 1), "${rand.nextInt(0, 9)}".toCharArray()[0]) }
            .joinToString("")
    }
}