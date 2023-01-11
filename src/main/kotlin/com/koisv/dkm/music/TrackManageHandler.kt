package com.koisv.dkm.music

import com.koisv.dkm.data.GuildData
import com.koisv.dkm.data.GuildData.RepeatType.*
import com.koisv.dkm.guildList
import com.koisv.dkm.instance
import com.koisv.dkm.music.TrackManageHandler.QueryType.*
import com.koisv.dkm.music.TrackManageHandler.UpdateType.*
import com.koisv.dkm.playerManager
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason.*
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.createChatInputCommand
import dev.kord.core.behavior.edit
import dev.kord.core.entity.application.GuildChatInputCommand
import dev.kord.core.kordLogger
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.subCommand
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TrackManageHandler {
    enum class QueryType { Single,Playlist,Search }
    enum class UpdateType { Volume, Stop, Full, Shuffle, Remove, Pause, Repeat }
    companion object {
        suspend fun getTrack(query: String, type: QueryType): List<AudioTrack> {
            return suspendCoroutine {
                playerManager.loadItem(query, object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack?) {
                        if (track != null) it.resume(listOf(track))
                        else it.resume(listOf())
                    }
                    override fun playlistLoaded(playlist: AudioPlaylist?) {
                        it.resume(
                            if (playlist != null) {
                                when (type) {
                                    Single -> {
                                        listOf(playlist.tracks.first())
                                    }
                                    Playlist -> {
                                        playlist.tracks
                                    }
                                    Search -> {
                                        playlist.tracks.take(5)
                                    }
                                }
                            } else listOf()
                        )
                    }
                    override fun noMatches() { it.resume(listOf()) }
                    override fun loadFailed(exception: FriendlyException?) { it.resume(listOf()) }
                })
            }
        }
        suspend fun guildControl(id: Snowflake, type: UpdateType, data: List<String?> = listOf(null,null)) {
            val guild = instance.getGuildOrNull(id)?.asGuild()
            if (guild != null) {
                suspend fun updateVolume(value: String) {
                    val exists = guild.getApplicationCommands()
                        .filter{
                            it is GuildChatInputCommand
                            it.name == "볼륨"
                        }.firstOrNull() as GuildChatInputCommand?
                    exists?.edit { description = "볼륨을 조절합니다. [$value%]" }
                        ?: guild.createChatInputCommand("볼륨","볼륨을 조절합니다. [$value%]") {
                            integer("값", "조절 할 볼륨 값을 입력해 주세요.") {
                                minValue = 0
                                maxValue = 500
                                required = true
                            }
                        }
                }
                suspend fun updateStop(value: String?) {
                    val exists = guild.getApplicationCommands()
                        .filter{
                            it is GuildChatInputCommand
                            it.name == "중지"
                        }.firstOrNull() as GuildChatInputCommand?
                    exists?.edit { description = "노래를 중단합니다. Now Playing - ${value?.take(40) ?: "노래 정보 없음"}" }
                        ?: guild.createChatInputCommand(
                            "중지","노래를 중단합니다. Now Playing - ${value?.take(40) ?: "노래 정보 없음"}"
                        )

                }
                suspend fun updateRepeat(targetGuild: GuildData) {
                    val exists = guild.getApplicationCommands()
                        .filter{
                            it is GuildChatInputCommand
                            it.name == "반복"
                        }.firstOrNull() as GuildChatInputCommand?
                    exists?.edit {
                        subCommand("한곡", "한곡만 반복합니다.") {}
                        subCommand("전체", "전곡을 반복합니다.") {}
                        subCommand("끄기", "반복을 중단합니다. | 현재 ${
                            when (targetGuild.repeat) {
                                None -> "반복 정지"
                                One -> "한곡 반복"
                                All -> "전곡 반복"
                            }
                        } 중") }
                        ?: guild.createChatInputCommand("반복", "노래 반복을 결정합니다.") {
                            subCommand("한곡", "한곡만 반복합니다.") {}
                            subCommand("전체", "전곡을 반복합니다.") {}
                            subCommand("끄기", "반복을 중단합니다. | 현재 ${
                                when (targetGuild.repeat) {
                                    None -> "반복 정지"
                                    One -> "한곡 반복"
                                    All -> "전곡 반복"
                                }
                            } 중") {}
                        }
                }
                suspend fun updatePause(unpause: Boolean = false) {
                    val exists = guild.getApplicationCommands()
                        .filter{
                            it is GuildChatInputCommand
                            (it.name == "일시정지" || it.name == "다시재생")
                        }.firstOrNull() as GuildChatInputCommand?
                    exists?.edit {
                        name = if (unpause) "일시정지" else "다시재생"
                        description = "노래를 ${if (unpause) "잠시 정지합니다." else "다시 재생합니다."}"
                    }
                        ?: guild.createChatInputCommand(
                            if (unpause) "일시정지" else "다시재생",
                            "노래를 ${if (unpause) "잠시 정지합니다." else "다시 재생합니다."}"
                        )

                }
                suspend fun updateShuffle(targetGuild: GuildData) {
                    val shuffle = targetGuild.shuffle
                    val exists = guild.getApplicationCommands()
                        .filter{
                            it is GuildChatInputCommand
                            it.name == "셔플"
                        }.firstOrNull() as GuildChatInputCommand?
                    exists?.edit { description = "셔플을 ${if (shuffle) "끕" else "켭"}니다." }
                        ?: guild.createChatInputCommand(
                            "셔플", "셔플을 ${if (shuffle) "끕" else "켭"}니다."
                        )
                }
                suspend fun createList() {
                    if (guild.getApplicationCommands().filter { it.name == "재생목록" }.firstOrNull() == null)
                        guild.createChatInputCommand("재생목록", "대기열에 있는 노래 목록을 표시합니다.")
                }
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                when (type) {
                    Volume -> updateVolume(data[0] ?: "ERROR")
                    Stop -> updateStop(data[0])
                    Shuffle -> updateShuffle(findGuild)
                    Pause -> updatePause(data[0].toBoolean())
                    Repeat -> updateRepeat(findGuild)
                    Full -> {
                        updateStop(data[0]); updateVolume(data[1] ?: "ERROR"); createList()
                        updatePause(true); updateShuffle(findGuild); updateRepeat(findGuild)
                    }
                    Remove -> {
                        guild.getApplicationCommands().filter {
                            it is GuildChatInputCommand
                            (it.name == "중지") || (it.name == "일시정지") || (it.name == "다시재생")
                        }.collect { (it as GuildChatInputCommand).delete() }
                    }
                }
                if (!guildList.contains(findGuild)) guildList.add(findGuild)
            }
        }

        suspend fun getInfo(link: String): List<String>? {
            return suspendCoroutine {
                playerManager.loadItem(link, object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack?) {
                        it.resume(
                            if (track != null)
                                listOf(track.info.title, track.info.author, track.info.length.toString())
                            else null
                        )
                    }
                    override fun playlistLoaded(playlist: AudioPlaylist?) {
                        it.resume(
                            if (playlist != null)
                                listOf(playlist.name, playlist.tracks.toString())
                            else null
                        )
                    }
                    override fun noMatches() { it.resume(null) }
                    override fun loadFailed(exception: FriendlyException?) { it.resume(null) }
                })
            }
        }
    }
    class Event: AudioEventAdapter() {
        override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
            if (player == null) return
            val findGuild = guildList.find { it.player == player } ?: return
            val nowPlaying = findGuild.trackList.find { it == track }
            if (nowPlaying != null) {
                when (findGuild.repeat) {
                    None -> findGuild.trackList.remove(track)
                    One -> findGuild.trackList.run {
                        val old = find { it == track }
                        if (old != null) { add(0, old.makeClone()); remove(old) }
                    }
                    All -> findGuild.trackList.run {
                        val old = find { it == track }
                        if (old != null) { add(old.makeClone()); remove(old) }
                    }
                }
                runBlocking {
                    findGuild.voiceLast?.edit { content = ":arrow_forward: 재생 시작! | ${track?.info?.title ?: "**노래 정보 없음**"}" }
                    guildControl(findGuild.id, Stop, listOf(track?.info?.title,null))
                }
            }
        }

        override fun onTrackStuck(
            player: AudioPlayer?,
            track: AudioTrack?,
            thresholdMs: Long,
            stackTrace: Array<out StackTraceElement>?
        ) {
            kordLogger.error("[M] 트랙 꼬임 발생")
            throw Exception().apply { this.stackTrace = stackTrace }
        }

        // Start next track

        // endReason == FINISHED: A track finished or died by an exception (mayStartNext = true).
        // endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
        // endReason == STOPPED: The player was stopped.
        // endReason == REPLACED: Another track started playing while this had not finished
        // endReason == CLEANUP: Player hasn't been queried for a while, if you want you can put a
        //                       clone of this back to your queue
        @OptIn(KordVoice::class)
        override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
            if (player == null) return
            val findGuild = guildList.find { it.player == player } ?: return
            val nowPlayer = findGuild.player
            when (endReason) {
                FINISHED -> {
                    runBlocking {
                        if (findGuild.trackList.isEmpty()) {
                            findGuild.connection?.shutdown()
                            findGuild.voiceLast?.edit { content = "재생이 끝나 중지되었습니다." }
                            guildControl(findGuild.id, Remove)
                        } else {
                            nowPlayer.playTrack(
                                if (findGuild.shuffle) findGuild.trackList.random()
                                else findGuild.trackList[0]
                            )
                        }
                    }
                }
                LOAD_FAILED -> {
                    runBlocking {
                        val lastMsg = findGuild.voiceLast
                        if (lastMsg != null) {
                            findGuild.voiceLast =
                                lastMsg.channel.asChannel().createMessage(
                                    "재생 중 오류가 발생했습니다. | [${track?.info?.title ?: "**트랙 정보 없음**"}]"
                                )
                            if (findGuild.trackList.isEmpty()) {
                                findGuild.connection?.shutdown()
                                findGuild.voiceLast?.edit { content += " | 마지막 곡입니다." }
                                guildControl(findGuild.id, Remove)
                            }
                        }
                        findGuild.trackList.remove(track)
                        nowPlayer.playTrack(
                            if (findGuild.shuffle) findGuild.trackList.random()
                            else findGuild.trackList[0]
                        )
                    }
                }
                STOPPED -> {
                    runBlocking<Unit> {
                        guildControl(findGuild.id, Remove)
                        findGuild.connection?.shutdown()
                        findGuild.voiceLast?.edit { content = "재생이 중지되었습니다." }
                    }
                }
                REPLACED -> {}
                CLEANUP -> {}
                null -> throw NullPointerException()
            }
        }

        override fun onTrackException(player: AudioPlayer?, track: AudioTrack?, exception: FriendlyException?) {
        }
    }
}