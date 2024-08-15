package com.koisv.dkm

import com.koisv.dkm.commands.CommandManager
import com.koisv.dkm.data.Bot
import com.koisv.dkm.data.DataManager
import com.koisv.dkm.data.GuildData
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
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
import kotlinx.coroutines.Job
import kotlinx.datetime.Instant
import mu.KLogger

val guildList: MutableList<GuildData> = mutableListOf()
val playerManager = DefaultAudioPlayerManager()
val debugGuildSet = ::debugGuild.isInitialized
val botGuildInit = ::botGuild.isInitialized
var debugCode : Int = 0
lateinit var autoSave : Job
lateinit var instance : Kord
lateinit var instanceBot : Bot
lateinit var debugGuild : GuildData
lateinit var botGuild: Guild
lateinit var Uptime: Instant
lateinit var logger: KLogger

suspend fun main(args: Array<String>) {
    val bots = DataManager.botLoad()
    guildList.addAll(DataManager.guildLoad())

    instanceBot =
        if (args.isNotEmpty() && args[0].startsWith("bot"))
            if (args[0].split(":").size != 2)
                throw IndexOutOfBoundsException("올바른 숫자를 입력해 주세요. | 예) bot:1")
            else bots[args[0].split(":")[1].toInt()] else bots[0]
    instance = Kord(instanceBot.token) {
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
    logger = mu.KLogging().logger

    if (instanceBot.isTest) logger.warn("!!!TESTMODE!!!")
    DataManager.dataCleanup(guildList)
    CommandManager.globalReg(instance)
    autoSave = DataManager.autoSave()

    AudioSourceManagers.registerRemoteSources(playerManager)
    instance.on<Event> { Events.handle(this) }

    instance.login {
        intents = Intents(Intents.NON_PRIVILEGED + Intent.GuildMessages + Intent.Guilds)
    }
}