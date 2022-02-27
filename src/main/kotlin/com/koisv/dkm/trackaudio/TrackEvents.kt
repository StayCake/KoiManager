package com.koisv.dkm.trackaudio

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.data.Convert
import com.koisv.dkm.trackList
import com.koisv.dkm.trackaudio.TrackControl.nextTrackPlay
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TrackEvents(private val event: ChatInputInteractionEvent) : AudioEventAdapter() {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java) as Logger

    override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            if (trackList[player] == null) logger.error("[M] 대체 이건 무슨 일이지? | $player") else {
                if (player != null) {
                    if (endReason != AudioTrackEndReason.LOAD_FAILED) nextTrackPlay(player, track, event)
                    else {
                        trackList[player]?.remove(track)
                        nextTrackPlay(player, null, event)
                    }
                }
            }
        }

        if (endReason == AudioTrackEndReason.LOAD_FAILED) {
            event.interaction.channel.subscribe {
                it.createMessage("노래 **${track?.info?.title ?: "제목 오류"}**를 재생하는 과정에서 문제가 발생했습니다.").subscribe()
            }
        }
        // Start next track

        // endReason == FINISHED: A track finished or died by an exception (mayStartNext = true).
        // endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
        // endReason == STOPPED: The player was stopped.
        // endReason == REPLACED: Another track started playing while this had not finished
        // endReason == CLEANUP: Player hasn't been queried for a while, if you want you can put a
        //                       clone of this back to your queue
    }

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        Replies.interactEdit(event,
            ":notes: " +
                    (track?.info?.title ?: "[**제목 오류**]") +
                    " [${Convert.timeStamp(track)}] 재생 시작!"
        )
    }
}