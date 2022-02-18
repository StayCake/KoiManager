package com.koisv.dkm.commands.music

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.musicPlayers
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

class Pause : SlashCmd {
    override val name: String = "일시정지"
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        val guild = event.interaction.guild.block() ?: return Replies.nonDM(event)
        if (musicPlayers.containsKey(guild.id)) {
            val current = musicPlayers[guild.id]?.isPaused
            if (current != null) musicPlayers[guild.id]?.isPaused = (current.not())
            else event.reply()
                .withEphemeral(true)
                .withContent("재생중이 아닙니다.").block()
            if (musicPlayers[guild.id]?.playingTrack == null) event.reply()
                .withEphemeral(true)
                .withContent("재생중이 아닙니다.").block()
            event.reply()
                .withEphemeral(false)
                .withContent("일시정지가 ${if(current == true) "해제" else "설정"} 되었습니다.").block()
        } else {
            event.reply()
                .withEphemeral(true)
                .withContent("재생중이 아닙니다.").block()
        }
        return Mono.empty()
    }
}