package com.koisv.dkm.commands

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

interface SlashCmd {
    val name: String
    fun handle(event: ChatInputInteractionEvent): Mono<Void>
}