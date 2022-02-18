package com.koisv.dkm.commands.music

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.guildConnections
import com.koisv.dkm.musicPlayers
import com.koisv.dkm.trackListeners
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import discord4j.core.`object`.entity.Guild
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.voice.VoiceConnection
import reactor.core.publisher.Mono

class Quit : SlashCmd {
    override val name: String = "중지"

    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        var current : VoiceConnection? = null
        for (i in guildConnections) {
            if (i.guildId == event.interaction.guildId.get()) current = i
        }
        return if (current != null) {
            val guild = event.interaction.guild.block() ?: return Replies.nonDM(event)
            val currentPlayer = musicPlayers[guild.id]
            localDisconnect(currentPlayer,guild)
            event.reply()
                .withEphemeral(false)
                .withContent("음성방을 퇴장했습니다!")
        } else {
            event.reply()
                .withEphemeral(true)
                .withContent("음성방에 있지 않습니다!")
        }
    }

    companion object {
        fun localDisconnect(player: AudioPlayer?,guild: Guild) {
            player?.stopTrack()
            player?.removeListener(trackListeners[player])
            guildConnections.removeIf { it.guildId == guild.id }
            guild.voiceConnection.subscribe {
                it.disconnect().subscribe().dispose()
            }.dispose()
        }
    }
}