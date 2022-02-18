package com.koisv.dkm.commands.utils

import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.data.Data
import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

class Setting : SlashCmd {
    override val name: String = "설정"

    private fun set(
        event: ChatInputInteractionEvent,
        loc: String,
        preoption: ApplicationCommandInteractionOption
    ) : Mono<Void> {
        val config = Data.getConf(event.interaction.guildId.get())
        return if (preoption.value.isEmpty) {
            config.channels[loc] = 0
            event.reply()
                .withEphemeral(true)
                .withContent("$loc 채널이 해제되었습니다.")
        } else {
            val targetId = preoption.value.get().asChannel().block()?.id?.asLong() ?: 0
            config.channels[loc] = targetId
            event.reply()
                .withEphemeral(true)
                .withContent("$loc 채널이 <#${targetId}>(으)로 설정되었습니다.")
        }
    }
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        when (event.options[0].name) {
            "채널" -> {
                val target = event.options[0].options[0].options[0]
                return set(event,event.options[0].options[0].name, target)
            }
        }
        return Mono.empty()
    }
}