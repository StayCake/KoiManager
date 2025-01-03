@file:OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class, DelicateCoroutinesApi::class)
package com.koisv.dkm

import com.koisv.dkm.Main.execute
import com.koisv.dkm.Main.mainLogger
import com.koisv.dkm.Main.mainUptime
import com.koisv.dkm.discord.KoiManager
import com.koisv.dkm.discord.data.Bot
import com.koisv.dkm.irc.IRCServer
import com.koisv.dkm.ktor.module
import io.ktor.server.application.Application
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

const val DEBUG_FLAG = "debug"

lateinit var discord: KoiManager
lateinit var ircServer: IRCServer
val ktor = embeddedServer(Jetty, configure = {
    configureServer = {
        connector {
            host = "0.0.0.0"
            port = 8921
        }
        connectionGroupSize = 4
        workerGroupSize = 8
        callGroupSize = 16
        shutdownGracePeriod = 5000
        shutdownTimeout = 10000
    }
    idleTimeout = 20.seconds
}, module = Application::module)
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

    suspend fun execute(args: Array<String>) {
        val superJob = supervisorScope {
            launch { ktor() }.job
            launch { irc() }.job
            launch { discord(args) }.job
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

    private fun dBotData(args: Array<String>): Bot? {
        val dBots = DataManager.Discord.botLoad()

        return if (dBots.isNotEmpty()) {
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
    }

    private fun waitForCompletion(job: Job) {
        while (!job.isCompleted) { /* Do nothing, just wait */
        }
    }
}