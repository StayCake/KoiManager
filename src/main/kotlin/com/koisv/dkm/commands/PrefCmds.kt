package com.koisv.dkm.commands

import com.koisv.dkm.data.Data
import com.koisv.dkm.guildConnections
import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.presence.ClientPresence
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object PrefCmds {
    private lateinit var targetEmbed: EmbedCreateSpec
    private lateinit var currentWorker: User

    private var noticeSetup = 0
    private var patchSetup = 0
    private val logger: Logger = LoggerFactory.getLogger(this::class.java) as Logger

    private fun allSend(msg: EmbedCreateSpec, channel: MessageChannel,event: MessageCreateEvent) {
        event.client.guilds.toStream().forEach { guild ->
            guild.selfMember.subscribe { self ->
                guild.channels.toStream().filter { it.type == Channel.Type.GUILD_TEXT }
                    .filter { guildChannel -> guildChannel.getEffectivePermissions(self).block()
                        ?.let { it.contains(Permission.SEND_MESSAGES) && it.contains(Permission.VIEW_CHANNEL) } == true }
                    .limit(1)
                    .forEach { guildChannel ->
                        (guildChannel as MessageChannel).createMessage(msg).subscribe()
                    }
            }
        }
        channel.createMessage("**강제** 전달 완료!").subscribe()
    }
    private fun allSend(msg: EmbedCreateSpec, channel: MessageChannel,event: MessageCreateEvent , kind: String) {
        Data.configList.forEach {
            val noticeId = it.channels[kind]
            if (noticeId != null && noticeId != 0L) {
                event.client.getChannelById(Snowflake.of(noticeId))
                    .subscribe { guildChannel ->
                        if (guildChannel is MessageChannel) {
                            guildChannel.createMessage(msg).subscribe()
                        }
                    }
            }
        }
        channel.createMessage("전달 완료!").subscribe()
    }

    fun command(event: MessageCreateEvent, gateway: GatewayDiscordClient) {
        when (event.message.content) {
            "!shutdown" -> {
                if (Data.ownerID == event.message.author.get().id.asString()) {
                    event.message.channel.subscribe {
                        it.createMessage("Shutting Down..").block()
                    }
                    gateway.self.block()?.client?.updatePresence(
                        ClientPresence.invisible()
                    )?.block()
                    for (i in guildConnections) {
                        i.disconnect().subscribe()
                    }
                    Data.save()
                    logger.info("Shutdown By ${event.message.author.get().tag}")
                    gateway.logout().then(gateway.onDisconnect()).block()
                }
            }
            "!serverlist" -> {
                if (Data.ownerID == event.message.author.get().id.asString()) {
                    event.message.channel.subscribe {
                        event.client.guilds.toStream().forEach { guild ->
                            logger.info(guild.name)
                        }
                        it.createMessage("콘솔을 확인하세요!").subscribe()
                    }
                }
            }
            "!notice" -> {
                if (Data.ownerID == event.message.author.get().id.asString()) {
                    if (patchSetup == 0) {
                        event.message.channel.subscribe {
                            if (noticeSetup != 0) {
                                noticeSetup = 0
                                it.createMessage("다시 설정합니다...").subscribe()
                            }
                            if (it.type == Channel.Type.DM) {
                                currentWorker = event.message.author.get()
                                targetEmbed = EmbedCreateSpec.create()
                                it.createMessage("공지 제목을 입력하세요.").subscribe()
                                noticeSetup++
                            }
                        }
                    }
                }
            }
            "!patch" -> {
                if (Data.ownerID == event.message.author.get().id.asString()) {
                    if (noticeSetup == 0) {
                        event.message.channel.subscribe {
                            if (patchSetup != 0) {
                                patchSetup = 0
                                it.createMessage("다시 설정합니다...").subscribe()
                            }
                            if (it.type == Channel.Type.DM) {
                                currentWorker = event.message.author.get()
                                targetEmbed = EmbedCreateSpec.create()
                                it.createMessage("패치노트를 입력하세요.").subscribe()
                                patchSetup++
                            }
                        }
                    }
                }
            }
            else -> {
                event.message.channel.subscribe { channel ->
                    if (channel.type == Channel.Type.DM) {
                        if (!this::currentWorker.isInitialized) return@subscribe
                        if (currentWorker == event.message.author.get()) {
                            when (noticeSetup) {
                                1 -> {
                                    val currentTime = ZonedDateTime.now()
                                    targetEmbed = targetEmbed
                                        .withTitle(event.message.content)
                                        .withFooter(
                                            EmbedCreateFields.Footer.of(
                                                currentTime.format(
                                                    DateTimeFormatter.ofPattern("yy년 MM월 dd일(E) | a hh:mm:ss z")
                                                        .withLocale(Locale.forLanguageTag("ko"))
                                                ),
                                                event.client.self.block()?.avatarUrl
                                            )
                                        )
                                    channel.createMessage(targetEmbed).subscribe()
                                    channel.createMessage("내용을 작성해주세요.").subscribe()
                                    noticeSetup++
                                }
                                2 -> {
                                    targetEmbed = targetEmbed.withFields(
                                        EmbedCreateFields.Field.of(
                                            "내용",
                                            "```${event.message.content}```",
                                            true
                                        )
                                    )
                                    channel.createMessage(targetEmbed).subscribe()
                                    channel.createMessage("아래 내용이 맞습니까? [y/n]").subscribe()
                                    noticeSetup++
                                }
                                3 -> {
                                    noticeSetup = 0
                                    when (event.message.content) {
                                        "y" -> {
                                            allSend(targetEmbed,channel, event, "공지")
                                        }
                                        "f" -> {
                                            allSend(targetEmbed, channel, event)
                                        }
                                        "n" -> channel.createMessage("취소되었습니다. 처음부터 다시 작업해 주세요.").subscribe()
                                    }
                                }
                            }
                            when (patchSetup) {
                                1 -> {
                                    targetEmbed = targetEmbed.withFields(
                                        EmbedCreateFields.Field.of(
                                            "내용",
                                            "```${event.message.content}```",
                                            true
                                        )).withTitle(Data.version + " 패치노트")
                                    channel.createMessage(targetEmbed).subscribe()
                                    channel.createMessage("아래 내용이 맞습니까? [y/n]").subscribe()
                                    patchSetup++
                                }
                                2 -> {
                                    patchSetup = 0
                                    when (event.message.content) {
                                        "y" -> {
                                            allSend(targetEmbed,channel,event ,"패치노트")
                                        }
                                        "n" -> channel.createMessage("취소되었습니다. 처음부터 다시 작업해 주세요.").subscribe()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}