package com.koisv.dkm.data

import discord4j.common.util.Snowflake
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.io.path.isRegularFile

object Data {
    @Serializable
    data class ServerConfig(
        val id: Long,
        var volume: Int = 10,
        var shuffle: Boolean = false,
        var repeat: Int = 0,
        var channels: MutableMap<String,Long> = mutableMapOf()
    )

    private val logger: Logger = LoggerFactory.getLogger(this::class.java) as Logger
    private val setting = File("./config.json")
    private val tokenJson = File("./token.json")
    private val guildConfFolder = File("./guildConf")
    private var testMode : Boolean = false
    val configList : MutableList<ServerConfig> = mutableListOf()
    var nologin : Boolean = false
    var token : String
        private set
    var ownerID : String
        private set
    lateinit var version : String

    fun save() {
        try {
            configList.forEach {
                val currentFile = File(guildConfFolder.path + "/${it.id}.json")
                currentFile.writeText(Json.encodeToString(it), Charset.defaultCharset())
            }
            logger.debug("[D] 서버 데이터 저장 완료.")
        } catch (e: Exception) {
            logger.error("[D] 쓰기 오류 발생! 서버 데이터를 수동으로 작업해주세요.")
            logger.error(configList.toString())
            logger.error(e.message)
        }
    }

    fun getConf(id: Snowflake) : ServerConfig {
        return if (configList.none { it.id == id.asLong() }) {
            val new = ServerConfig(id.asLong())
            configList.add(new)
            new
        } else {
            configList.filter { it.id == id.asLong() }[0]
        }
    }

    init {
        if (!tokenJson.exists()) {
            logger.info("토큰 파일이 없거나 읽을 수 없어 초기 설정을 진행합니다.")
            fun inftype() : String {
                val input = readLine().orEmpty()
                return if (input == "") {
                    println("올바른 값을 입력하세요 : ")
                    inftype()
                } else input
            }
            println("봇 토큰을 적어주세요 : ")
            token = inftype()
            println("제작자 ID를 적어주세요 : ")
            ownerID = inftype()
            tokenJson.writeText("""
            {
                "${if (!testMode) "main" else "test"}" : {
                    "data" : "$token",
                    "author" : "$ownerID"
                }
            }
        """.trimIndent())
            logger.debug("파일이 저장되었습니다.")
        } else {
            testMode = if (setting.canRead()) Json.parseToJsonElement(setting.readText())
                .jsonObject["test-mode"]?.jsonPrimitive?.boolean as Boolean else false
            nologin = if (setting.canRead()) Json.parseToJsonElement(setting.readText())
                .jsonObject["nologin"]?.jsonPrimitive?.boolean as Boolean else false
            if (testMode) logger.debug("테스트모드 가동중.")
            val tokenRead = Json.parseToJsonElement(tokenJson.readText())
            val mainData = tokenRead.jsonObject[if (!testMode) "main" else "test"]
            token = mainData?.jsonObject?.get("data")?.jsonPrimitive?.content.orEmpty()
            ownerID = mainData?.jsonObject?.get("author")?.jsonPrimitive?.content.orEmpty()
            version = mainData?.jsonObject?.get("version")?.jsonPrimitive?.content ?: "def1.0"
            if (token == "" || ownerID == "") logger.error("토큰 양식에 문제가 있습니다!\ntoken.json 을 삭제하고 다시 작업해 주세요.")
        }
        if (!guildConfFolder.isDirectory) guildConfFolder.mkdirs()
        val guildConf = Files.walk(guildConfFolder.toPath()).filter { it.isRegularFile() }
        guildConf.forEach {
            val rawData = it.toFile().readText()
            val data = Json.decodeFromString<ServerConfig>(rawData)
            configList.add(data)
        }
    }
}