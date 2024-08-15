package com.koisv.dkm.commands

import com.koisv.dkm.Events
import com.koisv.dkm.data.GuildData
import com.koisv.dkm.guildList
import com.koisv.dkm.instance
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.cache.data.OptionData
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.json.request.BulkDeleteRequest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock

@OptIn(KordVoice::class)
object UtilCommand {
    suspend fun cleanup(event: GuildChatInputCommandInteractionCreateEvent) {
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

    suspend fun settings(event: GuildChatInputCommandInteractionCreateEvent, subData: OptionData) {
        val reply = event.interaction.deferEphemeralResponse()
        val guild = event.interaction.guild.asGuildOrNull() ?: throw NullPointerException("Guild Not Found")
        val findGuild = guildList.find { it.id == guild.id } ?: GuildData(guild.name, guild.id)
        when (subData.name) {
            "채널" -> {
                val channel = event.interaction.command.strings["유형"]
                if (channel == null) event.interaction.respondEphemeral { content = "No Subcommand" }
                else {
                    val channelType = when (channel) {
                        "default" -> Events.NotifyChannel.Default
                        "update" -> Events.NotifyChannel.Update
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
}