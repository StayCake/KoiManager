package com.koisv.dkm

import com.koisv.dkm.MusicHandler.QueryType
import com.koisv.dkm.MusicHandler.UpdateType
import com.koisv.dkm.commands.AdminCommand
import com.koisv.dkm.commands.MusicCommand
import com.koisv.dkm.commands.UtilCommand
import com.koisv.dkm.data.Convert.getEnglishNumber
import com.koisv.dkm.data.Convert.getTimeStamp
import com.koisv.dkm.data.GuildData
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.DeferredMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.Event
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.guild.GuildDeleteEvent
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GlobalChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.InteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.*

object Events {
    enum class NotifyChannel { Update, Default }

    @OptIn(KordVoice::class)
    suspend fun handle(e: Event) {
        when (e) {
            is MessageCreateEvent -> {
                val target = e.message.content
                //println(target)
                val requestHeaders: MutableMap<String, String> = HashMap()
                requestHeaders["X-Naver-Client-Id"] = "bgxwlcHFPCR3Z4Vmf7sN"
                requestHeaders["X-Naver-Client-Secret"] = "8M2BEEV7Cf"

                //println(getLang("https://openapi.naver.com/v1/papago/detectLangs", requestHeaders, target)?.strName)
            }
            is InteractionCreateEvent -> {
                when (e) {
                    is GlobalChatInputCommandInteractionCreateEvent -> {
                        val response = e.interaction.deferEphemeralResponse()
                        response.respond { content = "개인 DM에서는 사용이 불가능 합니다." }
                    }
                    is GuildChatInputCommandInteractionCreateEvent -> {
                        if (guildList.find { it.id == e.interaction.guildId } == null)
                            guildList.add(GuildData(e.interaction.guild.asGuild().name, e.interaction.guildId))
                        logger.info { "[${e.interaction.guildId}] ${e.interaction.user.username} : ${e.interaction.command.rootName}" }
                        commandExecute(e)
                    }
                    is ButtonInteractionCreateEvent -> { buttonInteract(e) }
                    else -> {}
                }
            }
            is GuildCreateEvent -> {
                logger.info("[+] ${e.guild.name}")
                if (guildList.find { it.id == e.guild.id } == null)
                    guildList.add(GuildData(e.guild.name, e.guild.id))
            }
            is GuildDeleteEvent -> {
                logger.info("[-] ${e.guild?.name}")
                guildList.removeIf { it.id == e.guild?.id }
            }
            is ReadyEvent -> {
                logger.info("Logged On ${instance.getSelf().username} | ${instance.guilds.count()} Guilds")
                Uptime = Clock.System.now()
                instance.editPresence {
                    playing(instanceBot.presence)
                    since = Clock.System.now()
                }
            }
        }
    }

    @OptIn(KordVoice::class)
    suspend fun buttonInteract(event: ButtonInteractionCreateEvent) {
        val button = event.interaction
        val ident = button.componentId.split("|")
        if (ident.isEmpty()) return
        when (ident[0]) {
            "KoiDev" -> {
                event.interaction.respondPublic {
                    content = "TESTING"
                }
            }
            "MusicSearch" -> {
                if (button.message.interaction?.user?.id != button.user.id)
                    return kotlin.run {
                        button.respondEphemeral { content = "요청자만 사용할 수 있습니다!" }
                    }
                val track = MusicHandler.getTrack(ident[1], QueryType.Single)[0]

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
                            text = button.user.username
                            icon = button.user.avatar?.cdnUrl?.toUrl()
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

                    MusicHandler.guildControl(
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
                    embed(
                        fun EmbedBuilder.() {
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
                    )
                    actionRow(
                        fun ActionRowBuilder.() {
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
                    )
                }
            }
        }
    }

    suspend fun respondNonDM(it: DeferredMessageInteractionResponseBehavior) {
        return runBlocking { it.respond { content = "개인 DM에선 사용할 수 없습니다." } }
    }

    suspend fun respondError(it: GuildChatInputCommandInteractionCreateEvent) {
        return runBlocking { it.interaction.respondEphemeral { content = "명령 실행에 문제가 있습니다." } }
    }

    private suspend fun commandExecute(event: GuildChatInputCommandInteractionCreateEvent) {
        val subC1 = event.interaction.command.data.options.value?.get(0)
        val subC2 = subC1?.subCommands?.value?.get(0)
        when (event.interaction.command.rootName) {
            "반복" -> MusicCommand.repeat(
                event, subC2?.let { listOf(it.name) } ?: return respondError(event))
            "재생" -> MusicCommand.play(event)
            "셔플" -> MusicCommand.shuffle(event)
            "중지" -> MusicCommand.stop(event)
            "일시정지" -> MusicCommand.pause(event)
            "다시재생" -> MusicCommand.pause(event, true)
            "재생목록" -> MusicCommand.playlist(event)
            "볼륨" -> MusicCommand.volume(event)

            "청소" -> UtilCommand.cleanup(event)
            "설정" -> subC1?.let { UtilCommand.settings(event, it) } ?: return respondError(event)

            "dbg" -> AdminCommand.debug(event)
            "bmu" -> AdminCommand.manageBot(event, subC2, subC1?.name)
        }
    }
}
