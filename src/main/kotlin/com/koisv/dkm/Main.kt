package com.koisv.dkm

import com.koisv.dkm.commands.PrefCmds
import com.koisv.dkm.data.Data
import com.koisv.dkm.data.Data.configList
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.common.util.Snowflake
import discord4j.core.DiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.presence.ClientActivity
import discord4j.core.`object`.presence.ClientPresence
import discord4j.core.event.domain.guild.GuildCreateEvent
import discord4j.core.event.domain.guild.GuildDeleteEvent
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.voice.VoiceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

val playerManager : AudioPlayerManager = DefaultAudioPlayerManager()

val guildConnections = mutableListOf<VoiceConnection>()
val musicPlayers = mutableMapOf<Snowflake,AudioPlayer>()
val trackList = mutableMapOf<AudioPlayer,MutableList<AudioTrack>>()
val trackListeners = mutableMapOf<AudioPlayer, AudioEventAdapter>()
val lastTrack = mutableMapOf<AudioPlayer,AudioTrack>()

var lastUp : Long = 0

fun main() {
    playerManager.configuration.setFrameBufferFactory { buffer,data,atomic -> NonAllocatingAudioFrameBuffer(buffer,data,atomic) }
    AudioSourceManagers.registerRemoteSources(playerManager)

    Data
    if (!Data.nologin) {
        val client = DiscordClient.create(Data.token).gateway().withEventDispatcher { dispatcher ->
            dispatcher.on(ReadyEvent::class.java)
                .doFirst {
                    lastUp = System.currentTimeMillis()
                }
                .doOnNext {
                    it.self.client.updatePresence(
                        ClientPresence.online(
                            ClientActivity.playing("오류 내려고")
                        )
                    ).block()
                    val logger = LoggerFactory.getLogger(dispatcher.javaClass)
                    logger.info("${it.self.tag} | 준비 완료!")
                }
        }
        val logger = LoggerFactory.getLogger(client.javaClass)
        val downList = mutableListOf<Guild>()
        client.withGateway {
            mono {
                try {
                    CmdReg(it.restClient).registerCommands()
                } catch (e: Exception) {
                    logger.error("명령어 등록 오류 발생.", e)
                }
                it.on(GuildDeleteEvent::class.java).subscribe {
                    if (it.isUnavailable) {
                        downList.add(it.guild.get())
                        logger.error("${it.guild.get().name} 서버에 문제가 있는 듯 합니다...")
                    }
                    else {
                        configList.removeIf { conf -> conf.id == it.guild.get().id.asLong() }
                        logger.info("${it.guild.get().name} 서버에서 봇이 퇴출되었습니다.")
                    }
                }
                it.on(GuildCreateEvent::class.java).subscribe {
                    if (downList.contains(it.guild)) {
                        logger.info("아, ${it.guild.name} 서버가 돌아왔네요.")
                        downList.remove(it.guild)
                    }
                    else logger.info("새로운 서버 ${it.guild.name}에 발을 딛게 되었네요!")
                }
                it.on(MessageCreateEvent::class.java).subscribe { event ->
                    PrefCmds.command(event,it)
                }
                withContext(Dispatchers.IO) {
                    it.on(ChatInputInteractionEvent::class.java, SlashCmdListener::handle)
                        .then(it.onDisconnect()).block()
                }
                it.onDisconnect().block()
            }
        }.block()
    }
}
