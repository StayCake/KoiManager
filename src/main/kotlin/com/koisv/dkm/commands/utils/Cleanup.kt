package com.koisv.dkm.commands.utils

import com.koisv.dkm.commands.SlashCmd
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

class Cleanup : SlashCmd {
    override val name: String = "청소"
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        val target = event.interaction.channel.block()
        val count = if (event.options[0].value.isEmpty) 100 else event.options[0].value.get().asLong().toInt()
        if (count == 1) {
            target?.lastMessage?.block()?.delete()?.block()
        } else {
            if (target is GuildMessageChannel) {
                target.getLastMessageId().ifPresent { lastMessageId ->
                    target.bulkDelete(
                        target.getMessagesBefore(
                            lastMessageId
                        ).map(Message::getId).take(count.toLong())
                    ).blockFirst()
                }
            }
        }
        return event.reply()
            .withContent("메시지 ${count}개 청소 완료!")
            .withEphemeral(false)
    }
}