package com.koisv.dkm.trackaudio

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import discord4j.voice.AudioProvider
import java.nio.ByteBuffer

class TrackProvider(player: AudioPlayer) : AudioProvider() {

    private var player : AudioPlayer
    private val frame : MutableAudioFrame = MutableAudioFrame()

    init {
        // Allocate a ByteBuffer for Discord4J's AudioProvider to hold audio data for Discord
        ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize())
        // Set LavaPlayer's MutableAudioFrame to use the same buffer as the one we just allocated
        frame.setBuffer(buffer)
        this.player = player
    }

    override fun provide(): Boolean {
        // AudioPlayer writes audio data to its AudioFrame
        val didProvide: Boolean = player.provide(frame)
        // If audio was provided, flip from write-mode to read-mode
        if (didProvide) {
            buffer.flip()
        }
        return didProvide
    }
}