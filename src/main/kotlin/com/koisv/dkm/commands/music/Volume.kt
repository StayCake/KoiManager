package com.koisv.dkm.commands.music

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.data.Data.getConf
import com.koisv.dkm.musicPlayers
import com.koisv.dkm.playerManager
import com.koisv.dkm.trackaudio.TrackEvents
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

class Volume : SlashCmd {
    override val name: String = "볼륨"

    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        val guild = event.interaction.guild.block() ?: return Replies.nonDM(event)

        if (event.options.size == 0) return event.reply()
            .withEphemeral(true)
            .withContent("현재 볼륨은 ${getConf(guild.id).volume}% 입니다.")

        val current = if (musicPlayers.containsKey(guild.id)) {
            musicPlayers[guild.id]
        } else {
            val new = playerManager.createPlayer()
            new.addListener(TrackEvents(event))
            musicPlayers[guild.id] = new
            new
        }
        val value = event.options[0].value.get().asLong().toInt()
        current?.volume = value
        getConf(guild.id).volume = value
        return event.reply()
            .withEphemeral(false)
            .withContent("볼륨이 ${current?.volume}%로 설정되었습니다.")
    }
}