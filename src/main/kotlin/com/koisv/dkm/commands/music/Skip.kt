package com.koisv.dkm.commands.music

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.lastTrack
import com.koisv.dkm.musicPlayers
import com.koisv.dkm.trackList
import com.koisv.dkm.trackListeners
import com.koisv.dkm.trackaudio.TrackControl
import com.koisv.dkm.trackaudio.TrackEvents
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

class Skip : SlashCmd {
    override val name: String = "건너뛰기"
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {

        val target = event.interaction.member.get()
        val guild = target.guild.block() ?: return Replies.nonDM(event)

        val player = musicPlayers[guild.id]
        if (player == null) {
            return event.reply()
                .withEphemeral(true)
                .withContent("재생중이 아닙니다.")
        } else {
            player.removeListener(trackListeners[player])
            val listener = TrackEvents(event)
            trackListeners[player] = listener
            player.addListener(listener)
            trackList[player]?.remove(lastTrack[player])
            player.stopTrack()
            if (trackList[player]?.isEmpty() == true) {
                Quit.localDisconnect(player,guild)
                event.reply()
                    .withEphemeral(false)
                    .withContent("더는 재생할 곡이 없네요. 자동 퇴장할게요!").block()
            } else {
                event.reply()
                    .withEphemeral(false)
                    .withContent("건너뛸게요!").block()
                TrackControl.nextTrackPlay(player,null,event)
            }
            return Mono.empty()
        }
    }
}
