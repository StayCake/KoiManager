package com.koisv.dkm.discord.data

import com.koisv.dkm.discord.Events
import com.koisv.dkm.discord.KoiManager.Companion.playerManager
import com.koisv.dkm.discord.MusicHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.voice.VoiceConnection
import kotlin.io.encoding.ExperimentalEncodingApi


data class GuildData @OptIn(KordVoice::class, ExperimentalEncodingApi::class) constructor(
    var name: String,
    val id: Snowflake,
    var volume: Int = 10,
    var shuffle: Boolean = false,
    var repeat: RepeatType = RepeatType.None,
    val channels: MutableMap<Events.NotifyChannel, Snowflake?> = mutableMapOf(),
    val trackList: MutableList<AudioTrack> = mutableListOf(),
    var voiceLast : Message? = null,
    var connection : VoiceConnection? = null,
    val player: AudioPlayer = playerManager.createPlayer().apply { this.addListener(MusicHandler.Event()) },
) {
    enum class RepeatType { None,One,All }
}
