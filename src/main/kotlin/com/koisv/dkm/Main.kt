package com.koisv.dkm

import com.koisv.dkm.data.Bot
import com.koisv.dkm.data.DataManager
import com.koisv.dkm.data.GuildData
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.guild.GuildDeleteEvent
import dev.kord.core.event.interaction.GlobalChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.InteractionCreateEvent
import dev.kord.core.kordLogger
import dev.kord.core.on
import kotlinx.coroutines.flow.count
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KLogger

lateinit var instance : Kord

val guildList: MutableList<GuildData> = mutableListOf()
val playerManager = DefaultAudioPlayerManager()
val debugGuildSet = ::debugGuild.isInitialized
val botGuildInit = ::botGuild.isInitialized
var debugCode : Int = 0
lateinit var instanceBot : Bot
lateinit var debugGuild : GuildData
lateinit var botGuild: Guild
lateinit var Uptime: Instant
lateinit var logger: KLogger

suspend fun main(args: Array<String>) {
    val bots = DataManager.botLoad()
    val executeBot =
        if (args.isNotEmpty() && args[0].startsWith("bot"))
            if (args[0].split(":").size != 2)
                throw IndexOutOfBoundsException("올바른 숫자를 입력해 주세요. | 예) bot:1")
            else bots[args[0].split(":")[1].toInt()] else bots[0]

    if (executeBot.isTest) logger.warn("!!!TESTMODE!!!")
    guildList.addAll(DataManager.guildLoad())
    val kord = Kord(executeBot.token)
    instanceBot = executeBot
    instance = kord
    logger = kordLogger
    DataManager.dataCleanup(guildList)
    Commands.globalReg(kord)

    AudioSourceManagers.registerRemoteSources(playerManager)

    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        if (guildList.find { it.id == interaction.guildId } == null)
            guildList.add(GuildData(interaction.guild.asGuild().name, interaction.guildId))
        kordLogger.info { "[${interaction.guildId}] ${interaction.user.tag} : ${interaction.command.rootName}" }
        Events.commandExecute(this)
    }
    kord.on<GlobalChatInputCommandInteractionCreateEvent> {
        val response = interaction.deferEphemeralResponse()
        response.respond { content = "개인 DM에서는 사용이 불가능 합니다." }
    }
    kord.on<ReadyEvent> {
        kordLogger.info("Logged On ${instance.getSelf().tag} | ${instance.guilds.count()} Guilds")
        Uptime = Clock.System.now()
        kord.editPresence {
            playing(executeBot.presence)
            since = Clock.System.now()
        }
    }
    kord.on<InteractionCreateEvent> { Events.buttonInteract(this) }
    kord.on<GuildCreateEvent> {
        kordLogger.info("[+] ${guild.name}")
        if (guildList.find { it.id == guild.id } == null)
            guildList.add(GuildData(guild.name, guild.id))
    }
    kord.on<GuildDeleteEvent> {
        kordLogger.info("[-] ${guild?.name}")
        guildList.removeIf { it.id == guild?.id }
    }

    kord.login()
}