package com.koisv.dkm.discord

import com.koisv.dkm.DataManager
import com.koisv.dkm.discord.commands.CommandManager
import com.koisv.dkm.discord.data.Bot
import com.koisv.dkm.discord.data.GuildData
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import dev.kord.cache.map.MapLikeCollection
import dev.kord.cache.map.internal.MapEntryCache
import dev.kord.cache.map.lruLinkedHashMap
import dev.kord.core.Kord
import dev.kord.core.entity.Guild
import dev.kord.core.event.Event
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.NON_PRIVILEGED
import dev.lavalink.youtube.YoutubeAudioSourceManager
import kotlinx.coroutines.Job
import kotlinx.datetime.Instant
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlin.io.encoding.ExperimentalEncodingApi
import org.apache.logging.log4j.core.Logger as CoreLogger

@OptIn(ExperimentalEncodingApi::class)
class KoiManager(private val botData: Bot, val execArgs: Array<String>) {
    companion object {
        var sysVerfCode: Int = 0
        val dBotGInit = ::dBotG.isInitialized
        val debugGInit = ::dSysG.isInitialized
        val playerManager = DefaultAudioPlayerManager()
        val dGDataList: MutableList<GuildData> = mutableListOf()

        lateinit var autoSave : Job
        lateinit var dBotG: Guild
        lateinit var dSysG : GuildData
        lateinit var kordLogger: Logger
        lateinit var dBotUptime: Instant
        lateinit var dBotInstance : Kord
    }

    private val newYTASM = YoutubeAudioSourceManager()

    init {
        newYTASM.useOauth2(botData.token.youtube, botData.token.youtube.isNotEmpty())
        playerManager.registerSourceManager(newYTASM)
        dGDataList.addAll(DataManager.Discord.parseDGuilds())

        kordLogger = LogManager.getLogger("KM-DBot")
    }

    suspend fun start(noLogin: Boolean = false) {
        dBotInstance = Kord(botData.token.discord) {
            cache {
                messages { cache, description ->
                    MapEntryCache(cache, description, MapLikeCollection.lruLinkedHashMap(maxSize = 20))
                }
                guilds { cache, description ->
                    MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap())
                }
                members { cache, description ->
                    MapEntryCache(cache, description, MapLikeCollection.concurrentHashMap())
                }
            }
            enableShutdownHook = true
        }

        DataManager.Discord.dataCleanup(dGDataList)
        CommandManager.globalReg(botData, dBotInstance)

        if (botData.isTest) {
            (kordLogger as CoreLogger).level = Level.DEBUG
            (LogManager.getLogger("dev.kord") as CoreLogger).level = Level.DEBUG
            (LogManager.getLogger("dev.lavalink") as CoreLogger).level = Level.DEBUG
            kordLogger.warn("!!!TESTMODE!!! | Enabling Debug Logs...")
        }
        autoSave = DataManager.autoSave()

        dBotInstance.on<Event> { Events.handle(this, botData) }

        if (!noLogin) dBotInstance.login {
            intents = Intents(Intents.NON_PRIVILEGED + Intent.GuildMessages + Intent.Guilds)
        }
    }
}