package com.koisv.dkm.commands

import com.koisv.dkm.Events
import com.koisv.dkm.MusicHandler
import com.koisv.dkm.data.Convert.getEnglishNumber
import com.koisv.dkm.data.Convert.getTimeStamp
import com.koisv.dkm.data.GuildData
import com.koisv.dkm.guildList
import com.koisv.dkm.instance
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.*

@OptIn(KordVoice::class)
object MusicCommand {
    suspend fun play(event: GuildChatInputCommandInteractionCreateEvent) {
        val reply = event.interaction.deferPublicResponse()
        val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
        val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
        val link = event.interaction.command.strings["링크"]
        val player = findGuild.player
        val state = event.interaction.user.getVoiceStateOrNull()
            ?: return kotlin.run { reply.respond { content = "음성방에 입장해 주세요!" } }
        val channel = state.getChannelOrNull()
            ?: return kotlin.run { reply.respond { content = "채널 로딩 오류!" } }

        if (!guildList.contains(findGuild)) guildList.add(findGuild)

        if (link != null) {
            val type =
                when {
                    link.contains("http://") || link.contains("https://") ->
                        when {
                            link.contains("watch?v=") ->
                                if (link.contains("&list=")) MusicHandler.QueryType.Playlist
                                else MusicHandler.QueryType.Single
                            link.contains("playlist?list=") -> MusicHandler.QueryType.Playlist
                            else -> MusicHandler.QueryType.Single
                        }
                    else -> MusicHandler.QueryType.Search
                }
            val current = MusicHandler.getTrack(
                if (type != MusicHandler.QueryType.Search) link else "ytsearch: $link", type
            )
            val respond = when (current.size) {
                0 -> "발견된 노래가 없습니다!"
                1 -> ":notes: 노래 추가됨 | ${current[0].info.title}"
                else ->
                    if (type == MusicHandler.QueryType.Search) ":mag: 검색 진행중.. | $link"
                    else ":notes: 목록 추가됨 | ${MusicHandler.getInfo(link)?.get(0) ?: "목록 정보 없음"}"
            }
            if (current.isEmpty()) reply.respond { content = respond }
            else if (type != MusicHandler.QueryType.Search) {
                if (player.playingTrack != null) findGuild.trackList.addAll(current)
                else {
                    val newList = current.toMutableList()
                    val trackIndex =
                        if (current.size > 1 && findGuild.shuffle) Random().nextInt(current.size) else 0
                    if (current.size > 1) {
                        newList.removeAt(trackIndex)
                        findGuild.trackList.addAll(newList)
                    }

                    player.volume = findGuild.volume
                    player.playTrack(current[trackIndex])

                    MusicHandler.guildControl(
                        guild.id, MusicHandler.UpdateType.Full,
                        listOf(current[trackIndex].info.title, findGuild.volume.toString())
                    )

                    findGuild.connection =
                        VoiceConnection(event.gateway, instance.selfId, channel.id, channel.guildId)
                        {
                            audioProvider { AudioFrame.fromData(player.provide()?.data) }
                        }
                    findGuild.connection?.connect()
                    findGuild.voiceLast = event.interaction.channel
                        .createMessage(":arrow_forward: 재생 시작! | ${current[trackIndex].info.title}")
                }
                reply.respond { content = respond }
            } else {
                val replier = reply.respond { content = respond }
                replier.message.edit {
                    content = null
                    embed {
                        title = "검색 결과"
                        current.forEachIndexed { i, track ->
                            fields.add(
                                EmbedBuilder.Field().apply
                                {
                                    inline = false
                                    name = "${i + 1}. ${track.info.title}"
                                    value = "${track.info.author} [${track.getTimeStamp()}]"
                                }
                            )
                        }
                        description = "\"$link\""
                        footer {
                            text = "@${event.interaction.user.username}"
                            icon = event.interaction.user.avatar?.cdnUrl?.toUrl()
                        }
                        timestamp = Clock.System.now()
                    }
                    actionRow {
                        interactionButton(ButtonStyle.Primary, "MusicSearch|${current[0].info.uri}")
                        {emoji = DiscordPartialEmoji(null , "1️⃣") }
                        interactionButton(ButtonStyle.Primary, "MusicSearch|${current[1].info.uri}")
                        {emoji = DiscordPartialEmoji(null , "2️⃣") }
                        interactionButton(ButtonStyle.Primary, "MusicSearch|${current[2].info.uri}")
                        {emoji = DiscordPartialEmoji(null , "3️⃣") }
                        interactionButton(ButtonStyle.Primary, "MusicSearch|${current[3].info.uri}")
                        {emoji = DiscordPartialEmoji(null , "4️⃣") }
                        interactionButton(ButtonStyle.Primary, "MusicSearch|${current[4].info.uri}")
                        {emoji = DiscordPartialEmoji(null , "5️⃣") }
                    }
                }
            }
        } else {
            if (findGuild.trackList.isNotEmpty()) {
                if (player.playingTrack != null)
                    return runBlocking { reply.respond { content = "이미 재생중입니다!" } }
                val track = if (findGuild.shuffle) findGuild.trackList.random() else findGuild.trackList.first()

                player.playTrack(track)
                findGuild.connection = VoiceConnection(event.gateway, instance.selfId, channel.id, channel.guildId)
                {
                    audioProvider { AudioFrame.fromData(player.provide()?.data) }
                }
                findGuild.connection?.connect()
                findGuild.connection?.audioProvider.apply { AudioFrame.fromData(player.provide()?.data) }
                findGuild.voiceLast =
                    reply.respond { content = ":arrow_forward: 재생 시작! | ${track.info.title}" }.message
            } else {
                reply.respond { content = "재생목록이 텅 빈건가요..." }
            }
        }
    }

    suspend fun stop(event: GuildChatInputCommandInteractionCreateEvent) {
        val reply = event.interaction.deferPublicResponse()
        val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
        val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
        runBlocking { MusicHandler.guildControl(guild.id, MusicHandler.UpdateType.Remove) }
        findGuild.player.stopTrack()
        findGuild.connection?.disconnect()
        reply.respond { content = "재생이 정지되었습니다." }
    }

    suspend fun shuffle(event: GuildChatInputCommandInteractionCreateEvent) {
        val reply = event.interaction.deferPublicResponse()
        val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
        val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
        findGuild.shuffle = findGuild.shuffle.not()
        reply.respond { content = "셔플이 ${if (findGuild.shuffle) "켜" else "꺼" }졌습니다." }
        MusicHandler.guildControl(findGuild.id, MusicHandler.UpdateType.Shuffle)
    }

    suspend fun repeat(event: GuildChatInputCommandInteractionCreateEvent, subNames: List<String>?) {
        if (subNames == null) throw NullPointerException("subName is empty")
        val reply = event.interaction.deferPublicResponse()
        val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
        val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
        when (subNames[0]) {
            "전체" -> {
                findGuild.repeat = GuildData.RepeatType.All
            }
            "한곡" -> {
                findGuild.repeat = GuildData.RepeatType.One
            }
            "끄기" -> {
                findGuild.repeat = GuildData.RepeatType.None
            }
        }
        if (findGuild.player.playingTrack != null)
            findGuild.player.playingTrack?.makeClone()?.let {
                findGuild.trackList.add(if (findGuild.repeat == GuildData.RepeatType.One) 0 else (findGuild.trackList.size - 1), it)
            }
        reply.respond { content = "반복이 ${
            when (findGuild.repeat) {
                GuildData.RepeatType.None -> "꺼짐으"; GuildData.RepeatType.One -> "한곡으" ;GuildData.RepeatType.All -> "전체"
            }}로 설정되었습니다." }
        MusicHandler.guildControl(findGuild.id, MusicHandler.UpdateType.Repeat)
    }

    suspend fun pause(event: GuildChatInputCommandInteractionCreateEvent, unpause: Boolean = false) {
        if (unpause) {
            val reply = event.interaction.deferPublicResponse()
            val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
            val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
            runBlocking {
                findGuild.player.isPaused = false
                MusicHandler.guildControl(findGuild.id, MusicHandler.UpdateType.Pause, listOf("true"))
            }
            reply.respond { content = "다시 재생 중 - ${findGuild.player.playingTrack.info.title ?: "* 트랙 정보 없음"}" }
        } else {
            val reply = event.interaction.deferPublicResponse()
            val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
            val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
            runBlocking {
                findGuild.player.isPaused = true
                MusicHandler.guildControl(findGuild.id, MusicHandler.UpdateType.Pause, listOf("false"))
            }
            reply.respond { content = "일시 정지 중..." }
        }
    }

    suspend fun playlist(event: GuildChatInputCommandInteractionCreateEvent) {
        val reply = event.interaction.deferPublicResponse()
        val guild = event.interaction.guild.asGuild()
        val findGuild = guildList.find { it.id == guild.id } ?: throw NullPointerException("Guild Not Found")
        val nowTrack = findGuild.player.playingTrack
        if (findGuild.trackList.isEmpty() && nowTrack == null) {
            reply.respond { content = "재생목록이 텅 빈건가요..." }
        } else if (findGuild.trackList.isEmpty()) {
            reply.respond {
                embed {
                    title = "재생 목록"
                    description = "현재 재생 중 - ${nowTrack.info.title} [${nowTrack.getTimeStamp()}]"
                    field {
                        name = "`1/1 페이지`"
                        value = ":one: [${nowTrack.getTimeStamp()}] ${nowTrack.info.title}"
                    }
                    if (findGuild.shuffle) {
                        field {
                            inline = false
                            name = "셔플 모드 켜짐"
                            value = ":exclamation: 셔플 모드에서는 목록 순서대로 재생되지 않습니다."
                        }
                    }
                }
            }
        } else {
            val listPage = mutableListOf<AudioTrack>()
            listPage.addAll(findGuild.trackList)
            nowTrack?.let{ listPage.add(0, it) }
            val nowAvailable = nowTrack != null
            val pageCount = (listPage.count() - 1) / 5 + 1
            reply.respond {
                embed {
                    title = "재생 목록"
                    description = if (nowAvailable) "현재 재생 중 - ${nowTrack.info.title} [${nowTrack.getTimeStamp()}]"
                    else "전체 ${listPage.count()}곡"
                    field {
                        name = "`1/$pageCount 페이지`"
                        if (nowAvailable) value = ":arrow_forward: [${nowTrack.getTimeStamp()}] ${
                            nowTrack.info.title}${if (findGuild.trackList.isNotEmpty())"\n" else ""}"
                        findGuild.trackList.take(if (nowAvailable) 4 else 5).forEachIndexed { i, track ->
                            value += ":${
                                (if (nowAvailable) i + 2 else i + 1).getEnglishNumber()
                            }: [${track.getTimeStamp()}] ${track.info.title}\n"
                        }
                    }
                    if (findGuild.shuffle) {
                        field {
                            inline = false
                            name = "셔플 모드 켜짐"
                            value = ":exclamation: 셔플 모드에서는 목록 순서대로 재생되지 않습니다."
                        }
                    }
                }
                if (pageCount > 1) {
                    actionRow {
                        interactionButton(ButtonStyle.Danger, "Playlist|FIRST") {
                            emoji = DiscordPartialEmoji(null , "◀")
                            disabled = true
                        }
                        interactionButton(ButtonStyle.Primary, "Playlist|2")
                        {emoji = DiscordPartialEmoji(null , "▶")}
                    }
                }
            }
        }
    }

    suspend fun volume(event: GuildChatInputCommandInteractionCreateEvent) {
        val reply = event.interaction.deferPublicResponse()
        val guild = event.interaction.guild.asGuild()
        val findGuild = guildList.find { it.id == guild.id } ?: return Events.respondNonDM(reply)
        val value = event.interaction.command.integers["값"]
        val player = findGuild.player
        val respond = if (value != null) {
            val fin = value.toInt()
            findGuild.volume = fin
            player.volume = fin
            when (fin) {
                0 -> ":mute: 음소거 중..."
                else -> ":loud_sound: 볼륨이 $fin%로 설정되었습니다."
            }
        } else {
            ":sound: 현재 볼륨은 ${player.volume}% 입니다."
        }
        MusicHandler.guildControl(guild.id, MusicHandler.UpdateType.Volume, listOf(player.volume.toString()))
        reply.respond { content = respond }
    }
}