package com.koisv.dkm.data

import com.koisv.dkm.guildList
import com.koisv.dkm.instance
import com.koisv.dkm.logger
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalSerializationApi::class)
object DataManager {
    @OptIn(DelicateCoroutinesApi::class)
    fun autoSave(): Job {
        return GlobalScope.launch {
            while (true) {
                delay(3.minutes)
                logger.info("Autosaving...")
                try { guildSave() }
                catch (e: Exception) {
                    logger.error("Failed to Autosave!")
                    e.printStack()
                }
            }
        }
    }

    private val file = File("./guildData.dat")
    private val keyFile = File("./key.txt")

    fun botLoad() : List<Bot> {
        val file = File("./token.json")
        return if (!file.exists()) {
            file.createNewFile()
            listOf()
        } else {
            val inputStream = file.inputStream()
            val data = Json.decodeFromStream<List<Bot>>(inputStream)
            inputStream.close()
            return data
        }
    }

    suspend fun guildSave() {
        dataCleanup(guildList)
        if (!file.exists()) withContext(Dispatchers.IO) { file.createNewFile() }
        if (!keyFile.exists()) throw IOException("암호화용 키 파일[key.txt]이 없습니다!")
        val keys = keyFile.readLines()
        if (keys.size != 2) throw IOException("키 파일[key.txt] 형식이 올바르지 않습니다!")

        val byteArrayOS = ByteArrayOutputStream()
        Json.encodeToStream(guildList.toList(), byteArrayOS)
        val stringRaw = byteArrayOS.toString()
        withContext(Dispatchers.IO) { byteArrayOS.close() }

        val encryptedData = stringRaw.encryptCBC(keys[0], keys[1])
        val dataWriter = file.writer()
        withContext(Dispatchers.IO) {
            dataWriter.write(encryptedData)
            dataWriter.close()
        }

        logger.info("Save Complete.")
    }

    suspend fun dataCleanup(list: MutableList<GuildData>) {
        instance.guilds.collect {
            list.removeIf { data -> list.none { data.id == it.id } }
        }
        guildList.removeAll(guildList)
        guildList.addAll(list)
    }

    fun guildLoad() : MutableList<GuildData> {
        if (!file.exists()) return mutableListOf()
        if (!keyFile.exists()) throw IOException("암호화용 키 파일[key.txt]이 없습니다!")
        val keys = keyFile.readLines()
        if (keys.size != 2) throw IOException("키 파일[key.txt] 형식이 올바르지 않습니다!")

        val dataReader = file.reader()
        val encryptedData = dataReader.readText()
        dataReader.close()
        val decryptRaw = encryptedData.decryptCBC(keys[0], keys[1])

        val stringArray = decryptRaw.toByteArray()
        val inputStream = ByteArrayInputStream(stringArray)
        val data = Json.decodeFromStream<List<GuildData>>(inputStream)
        inputStream.close()
        return data.toMutableList()
    }

    private fun String.encryptCBC(key: String, iv: String): String {
        val ivSpec = IvParameterSpec(iv.toByteArray())
        val keySpec = SecretKeySpec(key.toByteArray(), "AES")    /// 키
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")     //싸이퍼
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)       // 암호화/복호화 모드
        val crypted = cipher.doFinal(this.toByteArray())

        return String(Base64.getEncoder().encode(crypted))
    }

    private fun String.decryptCBC(key: String, iv: String): String {
        val decodedByte: ByteArray = Base64.getDecoder().decode(this)
        val ivSpec = IvParameterSpec(iv.toByteArray())
        val keySpec = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        return String(cipher.doFinal(decodedByte))
    }
}