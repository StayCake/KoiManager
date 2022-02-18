package com.koisv.dkm.commands

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.spec.MessageEditSpec
import discord4j.discordjson.possible.Possible
import reactor.core.publisher.Mono
import java.util.*

object Replies {

    fun interactEdit(event: ChatInputInteractionEvent,message: String) {
        event.reply.subscribe {
            it.edit(
                MessageEditSpec.create()
                    .withContent(Possible.of(Optional.of(message)))
            ).subscribe()
        }
    }

    fun nonDM(event: ChatInputInteractionEvent) : Mono<Void> {
        return event.reply()
            .withEphemeral(true)
            .withContent("개인 DM에서 할 수 없는 명령어 이거나 서버에 문제가 있습니다.")
    }
}