package com.koisv.dkm.commands

import com.koisv.dkm.*
import com.koisv.dkm.data.Convert.createEmbed
import com.koisv.dkm.data.Convert.edit
import com.koisv.dkm.data.DataManager
import com.koisv.dkm.data.GuildData
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.*
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createInvite
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.application.GuildChatInputCommand
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object AdminCommand {
    @OptIn(KordVoice::class)
    suspend fun debug(event: GuildChatInputCommandInteractionCreateEvent) {
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
                    CommandManager.debugReload(instance)
                    reply.respond { content = "Success" }
                }
            }
        }
    }

    suspend fun manageBot(event: GuildChatInputCommandInteractionCreateEvent, subCommand: SubCommand? = null, subName: String? = null) {
        when (subName) {
            "guild" -> {
                if (subCommand == null) return Events.respondError(event)
                when (subCommand.name) {
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
                            target.getPermissions().values + Permission.Administrator
                            event.interaction.respondEphemeral { content = "GRANTED" }
                        }
                    }
                }
            }
            "command" -> {
                if (subCommand == null) return Events.respondError(event)
                val reply = event.interaction.deferPublicResponse()
                when (subCommand.name) {
                    "reload" -> {
                        CommandManager.globalReg(instance)
                        reply.respond { content = "Reload Complese." }
                    }
                    "cleanup" -> {
                        CommandManager.globalCleanUp(instance)
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
                if (subCommand == null) return Events.respondError(event)
                when (subCommand.name) {
                    "shutdown" -> {
                        runBlocking {
                            event.interaction.respondEphemeral { content = "Shutting Down." }
                            logger.info("Shutting Down - ${event.interaction.user.username}")
                            DataManager.guildSave()
                            if (autoSave.isActive) autoSave.cancel()
                            instance.logout()
                            Runtime.getRuntime().exit(0)
                        }
                    }
                    "report" -> {
                        val reply = event.interaction.deferEphemeralResponse()
                        reply.respond {
                            embed {
                                title = "System Report"
                                description = "Kord 0.8.1"
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
                                    text = event.interaction.user.username
                                    icon = event.interaction.user.avatar?.cdnUrl?.toUrl()
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
                        val target = event.interaction.guild.asGuildOrNull() ?: return Events.respondNonDM(reply)
                        target.getApplicationCommands().firstOrNull { it.name == "bmu" }?.delete()
                        reply.respond { content = "Closed" }
                    }
                }
            }
            "notice" -> {
                if (subCommand == null) return Events.respondError(event)
                when (subCommand.name) {
                    "create" -> {
                        event.interaction.respondPublic {
                            embed {
                                title = event.interaction.command.strings["title"] ?: "NONE"
                                description = event.interaction.command.strings["description"]
                                timestamp = Clock.System.now()
                                author {
                                    name = instance.getSelf().username
                                    icon = instance.getSelf().avatar?.cdnUrl?.toUrl()
                                    url = "https://www.koisv.com/"
                                }
                                footer {
                                    text = "Send by ${event.interaction.user.username}"
                                    icon = event.interaction.user.avatar?.cdnUrl?.toUrl()
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
                            val location = subCommand.options.value?.get(0)?.value ?: return kotlin.run {
                                event.interaction.respondEphemeral { content = "error" }
                            }
                            val locationType = when (location) {
                                "notice" -> Events.NotifyChannel.Default
                                "update" -> Events.NotifyChannel.Update
                                else -> return kotlin.run {
                                    event.interaction.respondEphemeral { content = "error" }
                                }
                            }
                            val enforce = event.interaction.command.booleans["enforce"] ?: false
                            if (enforce) {
                                guildList.forEach {
                                    val guild = instance.getGuild(it.id)
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
            "test" -> {
                //Papago.main("TEST", "bgxwlcHFPCR3Z4Vmf7sN", "8M2BEEV7Cf")
                event.interaction.respondEphemeral { content = "done" }
            }
        }
    }
}