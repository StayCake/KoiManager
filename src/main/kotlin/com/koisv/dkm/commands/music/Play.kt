package com.koisv.dkm.commands.music

import com.koisv.dkm.*
import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.data.Data.getConf
import com.koisv.dkm.trackaudio.TrackEvents
import com.koisv.dkm.trackaudio.TrackHandler
import com.koisv.dkm.trackaudio.TrackProvider
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.voice.AudioProvider
import reactor.core.publisher.Mono

class Play : SlashCmd {
    private fun connect(targetVoice: VoiceChannel,provider: AudioProvider) {
        var sameConnected = false
        for (i in guildConnections) {
            i.channelId.subscribe {
                if (it == targetVoice.id) sameConnected = true
            }
        }
        if (!sameConnected) {
            targetVoice.join()
                .withSelfDeaf(true)
                .withProvider(provider)
                .subscribe {
                guildConnections.add(it)
            }
        }
    }

    override val name: String = "재생"

    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        val target = event.interaction.member.get()
        val guild = target.guild.block() ?: return Replies.nonDM(event)
        val targetVoice = target.voiceState.block()?.channel?.block()

        if (!event.interaction.member.isPresent) return event.reply()
            .withEphemeral(true).withContent("개인 DM에서 사용할 수 없는 명령어 입니다.")
        if (targetVoice == null) return event.reply()
            .withEphemeral(true).withContent("음성방에 입장해주세요!")

        val player = if (musicPlayers.containsKey(guild.id)) {
            val current = musicPlayers[guild.id]
            if (current != null) current else {
                val new = playerManager.createPlayer()
                musicPlayers[guild.id] = new
                new
            }
        } else {
            val new = playerManager.createPlayer()
            musicPlayers[guild.id] = new
            new
        }

        val provider : AudioProvider = TrackProvider(player)
        if (trackList[player] == null) trackList[player] = mutableListOf()

        if (event.options.size == 0) return if (trackList[player]?.isEmpty() == true) {
            event.reply()
                .withEphemeral(true)
                .withContent("재생 할 곡이 없습니다.")
        } else {
            val listener = TrackEvents(event)
            trackListeners[player] = listener
            player.addListener(listener)

            player.removeListener(trackListeners[player])
            connect(targetVoice, provider)
            player.playTrack(trackList[player]?.get(0)?.makeClone())
            event.reply()
                .withEphemeral(false)
                .withContent("재생 준비중...")
        }

        connect(targetVoice, provider)

        val scheduler = if (musicPlayers.containsKey(guild.id)) {
            musicPlayers[guild.id]?.let { TrackHandler(it,event) }
        } else {
            val new = playerManager.createPlayer()
            musicPlayers[guild.id] = new
            TrackHandler(new,event)
        }
        val listener = TrackEvents(event)
        trackListeners[player] = listener
        player.addListener(listener)

        musicPlayers[guild.id]?.volume = getConf(guild.id).volume
        playerManager.loadItem(event.options[0].value.get().asString(),scheduler)
        return event.reply()
            .withEphemeral(false)
            .withContent("재생 준비중...")
    }
}