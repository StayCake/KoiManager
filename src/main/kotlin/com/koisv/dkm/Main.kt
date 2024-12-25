@file:OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class, DelicateCoroutinesApi::class)
package com.koisv.dkm

import com.koisv.dkm.Main.execute
import com.koisv.dkm.Main.mainLogger
import com.koisv.dkm.Main.mainUptime
import com.koisv.dkm.discord.KoiManager
import com.koisv.dkm.irc.IRCServer
import com.koisv.dkm.ktor.module
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi

const val DEBUG_FLAG = "debug"

lateinit var discord: KoiManager
lateinit var ircServer: IRCServer
val ktor = embeddedServer(Netty, port = 8921, host = "0.0.0.0", module = Application::module)
var debug = false

suspend fun main(args: Array<String>) {
    // Improved clarity by creating isDebugMode variable
    debug = args.contains(DEBUG_FLAG)
    mainLogger.info("부팅 시작 시간: $mainUptime")
    execute(args)
}

object Main : CoroutineScope {
    private val invalidBotException = IndexOutOfBoundsException("올바른 숫자를 입력해 주세요. | 예) bot:1")
    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job
    val mainLogger: Logger = LogManager.getLogger("Main")
    val mainUptime: Instant = Clock.System.now()
    val loggerGui = LoggerGUI()
    private val dBots = DataManager.Discord.botLoad()

    suspend fun execute(args: Array<String>) {
        val superJob = supervisorScope {
            launch { discord(args) }.job
            launch { irc() }.job
            launch { ktor() }.job
        }.job
        waitForCompletion(superJob)
    }

    private suspend fun discord(args: Array<String>): Boolean {
        val botData = dBotData(args)
        botData?.let {
            discord = KoiManager(it, args)
            mainLogger.info("디스코드 봇이 로드되었습니다. 디스코드 봇 실행 중...")
            discord.start(noLogin = args.contains("--no-login"))
        } ?: mainLogger.warn("디스코드 봇 로드 실패!")
        return true
    }

    private suspend fun irc(): Boolean {
        ircServer = async { IRCServer(6667) }.await()
        return ircServer.listen().start().also { if (!it) mainLogger.warn("IRC 서버 실행 실패!") }
    }

    private fun ktor(): Boolean {
        mainLogger.info("Ktor 실행 중...")
        ktor.start(wait = true)
        return true
    }

    private fun dBotData(args: Array<String>) =
        if (dBots.isNotEmpty()) {
            val botArgument = args.firstOrNull { it.startsWith("bot") }
            botArgument?.split(":")?.let {
                when (it.size) {
                    2 -> dBots[it[1].toIntOrNull() ?: throw invalidBotException]
                    else -> throw invalidBotException
                }
            } ?: dBots[0]
        } else {
            mainLogger.warn("봇 데이터가 없습니다! 디스코드 봇이 작동하지 않습니다.")
            null
        }

    private fun waitForCompletion(job: Job) {
        while (!job.isCompleted) { /* Do nothing, just wait */
        }
    }
}