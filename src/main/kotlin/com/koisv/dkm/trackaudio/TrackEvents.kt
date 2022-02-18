package com.koisv.dkm.trackaudio

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.music.Quit
import com.koisv.dkm.data.Convert
import com.koisv.dkm.data.Data
import com.koisv.dkm.trackList
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
                val guildConf = Data.getConf(event.interaction.guildId.get())
                val nextTrack = when (guildConf.repeat) {
                    0 -> {
                        if (!(0..1).contains(trackList[player]?.size)) {
                            trackList[player]?.indexOf(track)?.let { trackList[player]?.removeAt(it) }
                            trackList[player]?.get(
                                if (guildConf.shuffle) {
                                    (0 until (trackList[player]?.size?.minus(1) ?: 0)).random()
                                } else 0
                            )
                        } else null
                    }
                    1 -> {
                        if (trackList[player]?.size != 0) {
                            trackList[player]?.get(0)?.makeClone()?.let { trackList[player]?.add(it) }
                            trackList[player]?.indexOf(track)?.let { trackList[player]?.removeAt(it) }
                            trackList[player]?.get(
                                if (guildConf.shuffle) {
                                    (0 until (trackList[player]?.size?.minus(1) ?: 0)).random()
                                } else 0
                            )
                        } else null
                    }
                    2 -> {
                        if (trackList[player]?.size != 0) {
                            trackList[player]?.get(0)?.makeClone()
                        } else null
                    }
                    else -> null
                }
                if (nextTrack == null) {
                    event.interaction.guild.subscribe { Quit.localDisconnect(player, it) }
                    if (trackList[player]?.isNotEmpty() == true) trackList[player]?.clear()
                    Replies.interactEdit(event,"재생이 끝나 자동 퇴장 되었습니다.")
                } else player?.playTrack(nextTrack)
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