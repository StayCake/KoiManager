package com.koisv.dkm.data

import com.koisv.dkm.Events
import com.koisv.dkm.MusicHandler
import com.koisv.dkm.playerManager
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.voice.VoiceConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GuildData @OptIn(KordVoice::class) constructor(
    val name: String,
    val id: Snowflake,
    var volume: Int = 10,
    var shuffle: Boolean = false,
    var repeat: RepeatType = RepeatType.None,
    val channels: MutableMap<Events.NotifyChannel, Snowflake?> = mutableMapOf(),
    @Transient val trackList: MutableList<AudioTrack> = mutableListOf(),
    @Transient var voiceLast : Message? = null,
    @Transient var connection : VoiceConnection? = null,
    @Transient val player: AudioPlayer = playerManager.createPlayer().apply { this.addListener(MusicHandler.Event()) },
) {
    enum class RepeatType { None,One,All }
}
