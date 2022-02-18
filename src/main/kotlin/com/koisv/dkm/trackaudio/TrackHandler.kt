package com.koisv.dkm.trackaudio

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.music.Quit
import com.koisv.dkm.data.Convert
import com.koisv.dkm.trackList
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TrackHandler(private val player: AudioPlayer,private val event: ChatInputInteractionEvent) : AudioLoadResultHandler {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java) as Logger
    private val guild = event.interaction.guild.block()

    override fun trackLoaded(track: AudioTrack?) {
        if (track != null) {
            trackList[player]?.add(track)
            if (player.playingTrack == null) player.playTrack(trackList[player]?.get(0))
            else Replies.interactEdit(event,"${track.info?.title} [${Convert.timeStamp(track)}] 추가 완료!")
        } else {
            if (guild != null) Quit.localDisconnect(player,guild)
            Replies.interactEdit(
                event,"링크가 올바르지 않거나 문제가 있습니다!"
            )
        }
    }

    override fun playlistLoaded(playlist: AudioPlaylist?) {
        if (playlist != null) {
            playlist.tracks?.forEach {
                if (trackList[player] == null) trackList[player] = mutableListOf()
                trackList[player]?.add(it)
            }
            if (player.playingTrack == null) player.playTrack(trackList[player]?.get(0))
            Replies.interactEdit(event,":notes: 재생목록 " + (playlist.name ?: "[**제목 오류**]") + " 로딩 완료!")
        } else {
            if (guild != null) Quit.localDisconnect(player,guild)
            Replies.interactEdit(event,"링크가 올바르지 않거나 문제가 있습니다!")
        }
    }

    override fun noMatches() {
    }

    override fun loadFailed(exception: FriendlyException?) {
        logger.error("[M] 로딩 오류 : ${exception?.message}")
    }
}