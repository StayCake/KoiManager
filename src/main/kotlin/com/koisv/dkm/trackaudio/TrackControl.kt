package com.koisv.dkm.trackaudio

import com.koisv.dkm.commands.Replies
import com.koisv.dkm.commands.music.Quit
import com.koisv.dkm.data.Data
import com.koisv.dkm.lastTrack
import com.koisv.dkm.trackList
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent

object TrackControl {
    fun nextTrackPlay(player: AudioPlayer, track: AudioTrack?, event: ChatInputInteractionEvent) {
        val guildConf = Data.getConf(event.interaction.guildId.get())
        if (lastTrack[player] == null) track?.let { lastTrack[player] = it }
        val trackSize = trackList[player]?.size ?: 0
        val nextTrack = when (guildConf.repeat) {
            0 -> {
                if (trackSize > 0) {
                    track?.let {
                        trackList[player]?.indexOf(track)?.let { trackList[player]?.removeAt(it) }
                    }
                    val currentSize = trackList[player]?.size ?: 0
                    if (currentSize > 0) {
                        trackList[player]?.get(
                            if (guildConf.shuffle) {
                                (0 until trackSize).random()
                            } else 0
                        )?.let {
                            lastTrack[player] = it
                            return@let it
                        }
                    } else null
                } else null
            }
            1 -> {
                if (trackSize > 0) {
                    if (track != null) trackList[player]?.indexOf(track)?.let {
                        trackList[player]?.get(it)?.let { track ->
                            trackList[player]?.add(track.makeClone())
                        }
                        trackList[player]?.removeAt(it)
                    }
                    val currentSize = trackList[player]?.size ?: 0
                    if (currentSize > 0) {
                        trackList[player]?.get(
                            if (guildConf.shuffle) {
                                (0 until (trackList[player]?.size?.minus(1) ?: 0)).random()
                            } else 0
                        )?.let {
                            lastTrack[player] = it
                            return@let it
                        }
                    } else null
                } else null
            }
            2 -> {
                if (trackSize > 0) {
                    if (track != null) trackList[player]?.indexOf(lastTrack[player])
                        ?.let { index ->
                            trackList[player]?.get(index)?.let { lastTrack[player] = it }
                            trackList[player]?.get(index)?.makeClone()
                        }
                    else trackList[player]?.get(
                        if (guildConf.shuffle) {
                            (0 until (trackList[player]?.size?.minus(1) ?: 0)).random()
                        } else 0
                    )?.let {
                        lastTrack[player] = it
                        return@let it
                    }?.makeClone()
                } else null
            }
            else -> null
        }
        if (nextTrack == null) {
            event.interaction.guild.subscribe { Quit.localDisconnect(player, it) }
            if (trackList[player]?.isNotEmpty() == true) trackList[player]?.clear()
            Replies.interactEdit(event, "재생이 끝나 자동 퇴장 되었습니다.")
        } else {
            player.playTrack(nextTrack)
        }
    }
}