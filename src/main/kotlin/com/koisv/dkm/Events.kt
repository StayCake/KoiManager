package com.koisv.dkm

import com.koisv.dkm.data.DataManager
import com.koisv.dkm.data.GuildData
import com.koisv.dkm.data.GuildData.RepeatType.*
import com.koisv.dkm.music.TrackManageHandler
import com.koisv.dkm.music.TrackManageHandler.QueryType
import com.koisv.dkm.music.TrackManageHandler.UpdateType
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.*
import dev.kord.common.entity.optional.value
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createInvite
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Embed
import dev.kord.core.entity.Message
import dev.kord.core.entity.application.GuildChatInputCommand
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.InteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import dev.kord.rest.json.request.BulkDeleteRequest
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import io.ktor.network.sockets.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Events {
    private suspend fun respondNonDM(it: DeferredMessageInteractionResponseBehavior) {
        return runBlocking { it.respond { content = "개인 DM에선 사용할 수 없습니다." } }
    }

    private fun Int.getEnglishNumber() : String{
        return when (this) {
            1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; else -> "X" }
    }

    private fun AudioTrack.getTimeStamp() : String {
        return duration.toDuration(DurationUnit.MILLISECONDS)
            .toComponents { days, hours, minutes, seconds, _ ->
                when (days) {
                    0L -> {
                        when (hours) {
                            0 -> String.format("%02d:%02d", minutes, seconds)
                            else -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        }
                    }
                    else -> String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
                }
            }
    }

    enum class NotifyChannel { Update, Default }

    @OptIn(KordVoice::class)
    suspend fun commandExecute(event: GuildChatInputCommandInteractionCreateEvent) {
        val subC1 = event.interaction.command.data.options.value?.get(0)
        val subC2 = subC1?.subCommands?.value?.get(0)
        when (event.interaction.command.rootName) {
            "재생" -> {
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
                                        if (link.contains("&list=")) QueryType.Playlist
                                        else QueryType.Single
                                    link.contains("playlist?list=") -> QueryType.Playlist
                                    else -> QueryType.Single
                                }
                            else -> QueryType.Search
                        }
                    val current = TrackManageHandler.getTrack(
                        if (type != QueryType.Search) link else "ytsearch: $link", type
                    )
                    val respond = when (current.size) {
                        0 -> "발견된 노래가 없습니다!"
                        1 -> ":notes: 노래 추가됨 | ${current[0].info.title}"
                        else ->
                            if (type == QueryType.Search) ":mag: 검색 진행중.. | $link"
                            else ":notes: 목록 추가됨 | ${TrackManageHandler.getInfo(link)?.get(0) ?: "목록 정보 없음"}"
                    }
                    if (current.isEmpty()) reply.respond { content = respond }
                    else if (type != QueryType.Search) {
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

                            TrackManageHandler.guildControl(
                                guild.id, UpdateType.Full,
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
                                    text = event.interaction.user.tag
                                    icon = event.interaction.user.avatar?.url
                                }
                                timestamp = Clock.System.now()
                            }
                            actionRow {
                                interactionButton(ButtonStyle.Primary, "MusicSearch|${current[0].info.uri}")
                                {emoji = DiscordPartialEmoji(null , "1️⃣")}
                                interactionButton(ButtonStyle.Primary, "MusicSearch|${current[1].info.uri}")
                                {emoji = DiscordPartialEmoji(null , "2️⃣")}
                                interactionButton(ButtonStyle.Primary, "MusicSearch|${current[2].info.uri}")
                                {emoji = DiscordPartialEmoji(null , "3️⃣")}
                                interactionButton(ButtonStyle.Primary, "MusicSearch|${current[3].info.uri}")
                                {emoji = DiscordPartialEmoji(null , "4️⃣")}
                                interactionButton(ButtonStyle.Primary, "MusicSearch|${current[4].info.uri}")
                                {emoji = DiscordPartialEmoji(null , "5️⃣")}
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
            "반복" -> {
                val reply = event.interaction.deferPublicResponse()
                val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                if (subC1 == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                else when (subC1.name) {
                    "전체" -> {
                        findGuild.repeat = All
                    }
                    "한곡" -> {
                        findGuild.repeat = One
                    }
                    "끄기" -> {
                        findGuild.repeat = None
                    }
                }
                if (findGuild.player.playingTrack != null)
                    findGuild.player.playingTrack?.makeClone()?.let {
                        findGuild.trackList.add(if (findGuild.repeat == One) 0 else (findGuild.trackList.size - 1), it)
                    }
                reply.respond { content = "반복이 ${
                    when (findGuild.repeat) {
                        None -> "꺼짐으"; One -> "한곡으" ;All -> "전체"
                    }}로 설정되었습니다." }
                TrackManageHandler.guildControl(findGuild.id, UpdateType.Repeat)
            }
            "셔플" -> {
                val reply = event.interaction.deferPublicResponse()
                val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                findGuild.shuffle = findGuild.shuffle.not()
                reply.respond { content = "셔플이 ${if (findGuild.shuffle) "켜" else "꺼" }졌습니다." }
                TrackManageHandler.guildControl(findGuild.id, UpdateType.Shuffle)
            }
            "중지" -> {
                val reply = event.interaction.deferPublicResponse()
                val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                runBlocking { TrackManageHandler.guildControl(guild.id, UpdateType.Remove) }
                findGuild.player.stopTrack()
                findGuild.connection?.disconnect()
                reply.respond { content = "재생이 정지되었습니다." }
            }
            "일시정지" -> {
                val reply = event.interaction.deferPublicResponse()
                val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                runBlocking {
                    findGuild.player.isPaused = true
                    TrackManageHandler.guildControl(findGuild.id, UpdateType.Pause, listOf("false"))
                }
                reply.respond { content = "일시 정지 중..." }
            }
            "다시재생" -> {
                val reply = event.interaction.deferPublicResponse()
                val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                runBlocking {
                    findGuild.player.isPaused = false
                    TrackManageHandler.guildControl(findGuild.id, UpdateType.Pause, listOf("true"))
                }
                reply.respond { content = "다시 재생 중 - ${findGuild.player.playingTrack.info.title ?: "* 트랙 정보 없음"}" }
            }
            "재생목록" -> {
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
            "볼륨" -> {
                val reply = event.interaction.deferPublicResponse()
                val guild = event.interaction.guild.asGuild()
                val findGuild = guildList.find { it.id == guild.id } ?: return respondNonDM(reply)
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
                TrackManageHandler.guildControl(guild.id, UpdateType.Volume, listOf(player.volume.toString()))
                reply.respond { content = respond }
            }

            "청소" -> {
                val count = event.interaction.command.integers["청소량"] ?: 100
                val reply = event.interaction.deferPublicResponse()
                val respond =
                    if (
                        event.interaction.channel.asChannelOf<TextChannel>()
                            .getEffectivePermissions(instance.getSelf().asMember(event.interaction.guildId).id)
                            .contains(Permission.ManageMessages)
                        && event.interaction.channel.asChannelOf<TextChannel>()
                            .getEffectivePermissions(event.interaction.user.asMember().id)
                            .contains(Permission.ManageMessages)
                    ) {
                        val request = BulkDeleteRequest(
                            event.interaction.channel
                                .getMessagesBefore(event.interaction.getOriginalInteractionResponse().id, count.toInt())
                                .filter { (Clock.System.now() - it.timestamp).inWholeDays < 14 }.map { it.id }.toList()
                        )
                        if (request.messages.size > 1)
                            instance.rest.channel.bulkDelete(event.interaction.channelId, request,"/청소 명령어 사용")
                        ":broom: 메시지 ${request.messages.size}개를 청소했습니다. [오래된 메시지는 청소되지 않습니다]"
                    } else ":x: 권한이 부족합니다."
                reply.respond { content = respond }
            }
            "설정" -> {
                val reply = event.interaction.deferEphemeralResponse()
                val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                if (subC1 == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                else when (subC1.name) {
                    "채널" -> {
                        val channel = event.interaction.command.strings["유형"]
                        if (channel == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                        else {
                            val channelType = when (channel) {
                                "default" -> NotifyChannel.Default
                                "update" -> NotifyChannel.Update
                                else -> return kotlin.run {
                                    reply.respond { content = "잘못된 채널 유형입니다." }
                                }
                            }
                            val channelName = when (channel) {
                                "default" -> "공지사항"
                                "update" -> "패치노트"
                                else -> return kotlin.run {
                                    reply.respond { content = "잘못된 채널 유형입니다." }
                                }
                            }
                            val target = event.interaction.command.channels["대상"]
                            val respond = if (target == null) {
                                findGuild.channels[channelType] = null
                                "$channelName 채널 지정이 해제되었습니다."
                            } else {
                                findGuild.channels[channelType] = target.id
                                "$channelName 채널이 <#${target.id}>으로 설정되었습니다."
                            }
                            reply.respond { content = respond }
                        }
                    }
                }
            }

            "dbg" -> {
                val reply = event.interaction.deferEphemeralResponse()
                val getKey = event.interaction.command.integers["key"] ?: -1
                if (getKey.toInt() == -1) {
                    val code = (100000..999999).random()
                    debugCode = code
                    logger.info(code.toString())
                    reply.respond { content = "Debug Code Sent!" }
                } else {
                    if (!debugGuildSet) {
                        if (debugCode != getKey.toInt()) {
                            reply.respond { content = "올바른 코드를 입력하세요." }
                        } else {
                            val guild = event.interaction.guild.asGuildOrNull() ?: return
                            debugGuild = GuildData(guild.name, guild.id)
                            Commands.debugReload(instance)
                            reply.respond { content = "Success" }
                        }
                    }
                }
            }
            "bmu" -> {
                if (subC1 == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                else when (subC1.name) {
                    "guild" -> {
                        if (subC2 == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                        else when (subC2.name) {
                            "create" -> {
                                runBlocking<Unit> {
                                    val findBotG = instance.guilds.filter { it.isOwner }.firstOrNull()
                                        ?: instance.createGuild("KoiManager-Dev") {
                                            explicitContentFilter = ExplicitContentFilter.AllMembers
                                        }
                                    if (!botGuildInit) botGuild = findBotG
                                    val invite = (botGuild.getChannel(
                                        botGuild.channels.filter { it.type == ChannelType.GuildText }.first().id
                                    ) as TextChannel).createInvite { maxAge = 1.minutes; maxUses = 1 }
                                    event.interaction.respondEphemeral { content = "https://discord.gg/${invite.code}" }
                                }
                            }
                            "delete" -> {
                                val findBotG = instance.guilds.filter { it.isOwner }.firstOrNull()
                                    ?: return runBlocking { event.interaction.respondEphemeral { content = "Guild Not Found" } }
                                return runBlocking {
                                    event.interaction.respondEphemeral { content = "CLOSING" }
                                    findBotG.delete()
                                }
                            }
                            "grant" -> {
                                val findBotG = instance.guilds.filter { it.isOwner }.firstOrNull()
                                    ?: return runBlocking { event.interaction.respondEphemeral { content = "Guild Not Found" } }
                                return runBlocking {
                                    val target = findBotG.members.firstOrNull { it.asUser().id == event.interaction.user.id }
                                        ?: return@runBlocking runBlocking {
                                            event.interaction.respondEphemeral {
                                                content = "User Not Found"
                                            }
                                        }
                                    target.getPermissions().plus(permission = Permission.Administrator)
                                    event.interaction.respondEphemeral { content = "GRANTED" }
                                }
                            }
                        }
                    }
                    "command" -> {
                        val reply = event.interaction.deferPublicResponse()
                        if (subC2 == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                        else when (subC2.name) {
                            "reload" -> {
                                Commands.globalReg(instance)
                                reply.respond { content = "Reload Complese." }
                            }
                            "cleanup" -> {
                                Commands.globalCleanUp(instance)
                                reply.respond { content = "Cleaned Up." }
                            }
                            "guildclean" -> {
                                val findGuild =
                                    guildList.find {
                                        it.id == Snowflake(event.interaction.command.strings["id"] ?: "")
                                    }
                                        ?: return runBlocking {
                                            event.interaction.respondEphemeral { content = "NOT FOUND" }
                                        }
                                instance.getGuildOrNull(findGuild.id)?.getApplicationCommands()
                                    ?.filter { it is GuildChatInputCommand }?.collect {
                                        (it as GuildChatInputCommand).delete()
                                    }
                                reply.respond { content = "Cleaned Up." }
                            }
                        }
                    }
                    "system" -> {
                        if (subC2 == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                        else when (subC2.name) {
                            "shutdown" -> {
                                runBlocking {
                                    event.interaction.respondEphemeral { content = "Shutting Down." }
                                    logger.info("Shutting Down - ${event.interaction.user.tag}")
                                    DataManager.guildSave()
                                    instance.logout()
                                    Runtime.getRuntime().exit(0)
                                }
                            }
                            "report" -> {
                                val reply = event.interaction.deferEphemeralResponse()
                                reply.respond {
                                    embed {
                                        title = "System Report"
                                        description = "Kord 0.8.0-SNAPSHOT"
                                        field {
                                            inline = true
                                            name = "JVM"
                                            value = "`${
                                                System.getProperty("java.runtime.name")
                                            } ${
                                                System.getProperty("java.version")
                                            }\nKotlin ${KotlinVersion.CURRENT}`"
                                        }
                                        field {
                                            inline = true
                                            name = "OS"
                                            value = "`${System.getProperty("os.name")} | ${System.getProperty("os.arch")}`"
                                        }
                                        field {
                                            val runtime = Runtime.getRuntime()
                                            inline = true
                                            name = "Memory"
                                            value = "`Free - ${runtime.freeMemory() / 262144}MB\n" +
                                                    "Allocate - ${runtime.totalMemory() / 262144}MB\n" +
                                                    "Max - ${runtime.maxMemory() / 262144}MB`"
                                        }
                                        field {
                                            inline = true
                                            name = "Ping"
                                            value = "`${(instance.gateway.averagePing ?: Duration.ZERO).inWholeMilliseconds} ms`"
                                        }
                                        field {
                                            inline = true
                                            name = "Servers"
                                            value = "`${instance.guilds.count()}`"
                                        }
                                        field {
                                            inline = true
                                            name = "Uptime"
                                            value = "`${(Clock.System.now() - Uptime).toComponents {
                                                    days, hours, minutes, seconds, _ ->
                                                var res = "${seconds}초"
                                                if (minutes != 0) res = "${minutes}분 " + res
                                                if (hours != 0) res = "${minutes}시간 " + res
                                                if (days != 0L) res = "${minutes}일 " + res
                                                res
                                            }}`"
                                        }
                                        footer {
                                            text = event.interaction.user.tag
                                            icon = event.interaction.user.avatar?.url
                                        }
                                        timestamp = Clock.System.now()
                                    }
                                }
                            }
                            "save" -> {
                                runBlocking {
                                    DataManager.guildSave()
                                    event.interaction.respondEphemeral { content = "Data Saved." }
                                }
                            }
                            "close" -> {
                                val reply = event.interaction.deferEphemeralResponse()
                                val target = event.interaction.guild.asGuildOrNull() ?: return respondNonDM(reply)
                                target.getApplicationCommands().firstOrNull { it.name == "bmu" }?.delete()
                                reply.respond { content = "Closed" }
                            }
                        }
                    }
                    "notice" -> {
                        if (subC2 == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                        else when (subC2.name) {
                            "create" -> {
                                event.interaction.respondPublic {
                                    embed {
                                        title = event.interaction.command.strings["title"] ?: "NONE"
                                        description = event.interaction.command.strings["description"]
                                        timestamp = Clock.System.now()
                                        author {
                                            name = instance.getSelf().username
                                            icon = instance.getSelf().avatar?.url
                                            url = "https://www.koisv.com/"
                                        }
                                        footer {
                                            text = "Send by ${event.interaction.user.tag}"
                                            icon = event.interaction.user.avatar?.url
                                        }
                                    }
                                }
                            }
                            "send" -> {
                                val response = event.interaction.deferPublicResponse()
                                val target = event.interaction.channel.getMessagesBefore(
                                    event.interaction.getOriginalInteractionResponse().id
                                ).firstOrNull()
                                if (target != null && target.embeds.isNotEmpty()) {
                                    val location = subC2.options.value?.get(0)?.value ?: return kotlin.run {
                                        event.interaction.respondEphemeral { content = "error" }
                                    }
                                    val locationType = when (location) {
                                        "notice" -> NotifyChannel.Default
                                        "update" -> NotifyChannel.Update
                                        else -> return kotlin.run {
                                            event.interaction.respondEphemeral { content = "error" }
                                        }
                                    }
                                    val enforce = event.interaction.command.booleans["enforce"] ?: false
                                    if (enforce) {
                                        guildList.forEach {
                                            val guild = instance.getGuildOrThrow(it.id)
                                            guild.channels.filter{
                                                    channel ->
                                                (channel.type == ChannelType.GuildText
                                                        || channel.type == ChannelType.GuildNews)
                                                        && channel.getEffectivePermissions(instance.getSelf().id)
                                                    .contains(Permissions(Permission.ViewChannel, Permission.SendMessages))
                                            }.first().asChannelOf<TextChannel>().createEmbed(target.embeds[0])
                                        }
                                        response.respond { content = "complete [Enforce]" }
                                    }
                                    else guildList.forEach {
                                        it.channels[locationType]?.let {
                                                id -> instance.getChannelOf<TextChannel>(id)
                                            ?.createEmbed(target.embeds[0])
                                        }
                                        response.respond { content = "complete [default]" }
                                    }
                                } else response.respond { content = "Message Not Found or Wrong Message" }
                            }
                            "add" -> {
                                val response = event.interaction.deferPublicResponse()
                                val target = event.interaction.channel.getMessagesBefore(
                                    event.interaction.getOriginalInteractionResponse().id
                                ).firstOrNull()
                                val fieldTitle = event.interaction.command.strings["name"] ?: "-"
                                val fieldValue = event.interaction.command.strings["value"] ?: "-"
                                if (target != null && target.embeds.isNotEmpty()) {
                                    target.edit(target.embeds[0], listOf(fieldTitle, fieldValue))
                                    response.delete()
                                } else response.respond { content = "Message Not Found or Wrong Message" }
                            }
                        }
                    }
                    "test" -> {}
                }
            }
        }
    }

    @OptIn(KordVoice::class)
    suspend fun buttonInteract(event: InteractionCreateEvent) {
        if (event.interaction.type != InteractionType.Component) return
        val button = event.interaction as ButtonInteraction
        val ident = button.componentId.split("|")
        if (ident.isEmpty()) return
        when (ident[0]) {
            "KoiDev" -> {
                (event.interaction as ButtonInteraction).respondPublic {
                    content = "TESTING"
                }
            }
            "MusicSearch" -> {
                if (button.message.interaction?.user?.id != button.user.id)
                    return kotlin.run {
                        button.respondEphemeral { content = "요청자만 사용할 수 있습니다!" }
                    }
                val track = TrackManageHandler.getTrack(ident[1], QueryType.Single)[0]

                button.message.edit {
                    val originEmbed = button.message.embeds[0]
                    embed {
                        title = "검색 완료"
                        description = "${originEmbed.description}"
                        field {
                            inline = false
                            name = "선택 완료됨"
                            value = "${track.info.title} [${track.getTimeStamp()}]"
                        }
                        footer {
                            text = button.user.tag
                            icon = button.user.avatar?.url
                        }
                        timestamp = Clock.System.now()
                    }
                    actionRow {
                        button.message.actionRows[0].components.forEach {
                            interactionButton(
                                if ((it.data.customId.value?.split("|")?.get(1) ?: "") == ident[1]) ButtonStyle.Success
                                else ButtonStyle.Primary
                                , "DISABLED|${UUID.randomUUID()}"
                            ) {
                                disabled = true
                                emoji = it.data.emoji.value
                            }
                        }
                    }
                    content = null
                }

                val reply = button.deferPublicResponse()
                val origin = button.getOriginalInteractionResponseOrNull()
                    ?: return kotlin.run {}
                val guild = origin.getGuildOrNull() ?: return respondNonDM(reply)
                val state = button.user.asMember(guild.id).getVoiceStateOrNull()
                    ?: return kotlin.run { reply.respond { content = "음성방에 입장해 주세요!" } }
                val channel = state.getChannelOrNull()
                    ?: return kotlin.run { reply.respond { content = "채널 로딩 오류!" } }
                val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
                val player = findGuild.player

                if (player.playingTrack != null) {
                    findGuild.trackList.add(track)
                    reply.respond {
                        content = ":musical_note: 노래 추가됨 | ${track.info.title}"
                    }
                }
                else {
                    player.volume = findGuild.volume
                    player.playTrack(track)

                    TrackManageHandler.guildControl(
                        guild.id, UpdateType.Full,
                        listOf(track.info.title, findGuild.volume.toString())
                    )
                    findGuild.connection =
                        VoiceConnection(event.gateway, instance.selfId, channel.id, channel.guildId)
                        {
                            audioProvider { AudioFrame.fromData(player.provide()?.data) }
                        }
                    findGuild.connection?.connect()
                    findGuild.voiceLast = origin.asMessage()
                    reply.respond {
                        content = ":arrow_forward: 재생 시작! | ${track.info.title}"
                    }
                }
            }
            "Playlist" -> {
                val findGuild = guildList.find { it.id == button.message.getGuild().id }
                    ?: return respondNonDM(button.deferEphemeralResponse())
                val listPage = mutableListOf<AudioTrack>()
                listPage.addAll(findGuild.trackList)
                val nowTrack = findGuild.player.playingTrack
                nowTrack?.let{ listPage.add(0, it) }
                val nowAvailable = nowTrack != null
                val pageCount = (listPage.count() - 1) / 5 + 1
                val identInt = ident[1].toInt()
                if (identInt == 0 || identInt > pageCount) return kotlin.run {
                    button.message.delete()
                    button.respondPublic {
                        content = "오래된 재생목록입니다. 명령어를 다시 사용해 주세요."
                    }
                }
                button.deferEphemeralResponse().delete()
                button.message.edit {
                    embed {
                        title = "재생 목록"
                        description = if (nowAvailable) "현재 재생 중 - ${nowTrack.info.title} [${nowTrack.getTimeStamp()}]"
                        else "전체 ${listPage.count()}곡"
                        field {
                            name = "`${ident[1]}/$pageCount 페이지`"
                            if (nowAvailable && identInt == 1) value = ":arrow_forward: [${nowTrack.getTimeStamp()}] ${
                                nowTrack.info.title}${if (findGuild.trackList.isNotEmpty())"\n" else ""}"
                            findGuild.trackList
                                .slice((if (identInt == 1) 0 else ((identInt - 1) * 5) - 1) until identInt * 5 - 1
                                ).forEachIndexed { i, track ->
                                    value += ":${
                                        (if (nowAvailable && identInt == 1) i + 2 else i + 1).getEnglishNumber()
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
                    actionRow {
                        interactionButton(
                            if (identInt == 1) ButtonStyle.Danger else ButtonStyle.Primary,
                            "Playlist|${identInt - 1}") {
                            emoji = DiscordPartialEmoji(null , "◀")
                            if (identInt == 1) disabled = true
                        }
                        interactionButton(
                            if (identInt + 1 > pageCount) ButtonStyle.Danger else ButtonStyle.Primary,
                            "Playlist|${identInt + 1}")
                        {
                            emoji = DiscordPartialEmoji(null , "▶")
                            if (identInt + 1 > pageCount) disabled = true
                        }
                    }
                }
            }
        }
    }

    private fun receiveWork(embedBuilder: EmbedBuilder, recvEmbed: Embed) {
        val recvAuthor = recvEmbed.author
        if (recvAuthor != null) embedBuilder.author {
            name = recvAuthor.name
            icon = recvAuthor.iconUrl
            url = recvAuthor.url
        }
        val recvFooter = recvEmbed.footer
        if (recvFooter != null) embedBuilder.footer {
            text = recvFooter.text
            icon = recvFooter.iconUrl
        }
    }

    private suspend fun TextChannel.createEmbed(embed: Embed) {
        createEmbed {
            title = embed.title ?: "ERROR"
            description = embed.description
            timestamp = embed.timestamp
            embed.fields.forEach {
                field {
                    name = it.data.name
                    value = it.data.value
                    inline = it.data.inline.value ?: false
                }
            }
            receiveWork(this, embed)
        }
    }

    private suspend fun Message.edit(originEmbed: Embed, fieldData: List<String>) {
        edit {
            embed {
                title = originEmbed.title ?: "ERROR"
                description = originEmbed.description
                originEmbed.fields.forEach {
                    fields.add(EmbedBuilder.Field().apply {
                        name = it.name
                        value = it.value
                        inline = it.inline
                    })
                }
                fields.add(EmbedBuilder.Field().apply {
                    name = fieldData[0]
                    value = fieldData[1]
                    inline = false
                })
                timestamp = originEmbed.timestamp
                receiveWork(this, originEmbed)
            }
        }
    }
}
