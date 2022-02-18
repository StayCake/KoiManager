package com.koisv.dkm.commands.music

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.guildConnections
import com.koisv.dkm.musicPlayers
import com.koisv.dkm.trackList
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.voice.VoiceConnection
import reactor.core.publisher.Mono

class Clear : SlashCmd {
    override val name: String = "클리어"
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        var current : VoiceConnection? = null
        for (i in guildConnections) {
            if (i.guildId == event.interaction.guildId.get()) current = i
        }
        return if (current != null) {
            val guild = event.interaction.guild.block() ?: return Replies.nonDM(event)
            val player = musicPlayers[guild.id]
            trackList[player]?.clear()
            Quit.localDisconnect(player,guild)
            return event.reply()
                .withContent("모든 재생목록을 비웠습니다. [자동 퇴장]")
                .withEphemeral(false)
        } else {
            event.reply()
                .withEphemeral(true)
                .withContent("음성방에 있지 않습니다!")
        }
    }
}