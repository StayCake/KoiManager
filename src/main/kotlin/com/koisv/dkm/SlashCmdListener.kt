package com.koisv.dkm

import com.koisv.dkm.commands.*
import com.koisv.dkm.commands.music.*
import com.koisv.dkm.commands.utils.Cleanup
import com.koisv.dkm.commands.utils.Setting
import com.koisv.dkm.commands.utils.Stats
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

object SlashCmdListener {
    private val commands: MutableList<SlashCmd> = ArrayList()
    init {
        commands.addAll(
            arrayOf(
                Quit(), Play(),
                Pause(), Volume(), Skip(),
                Repeat(),Shuffle(), Cleanup(),
                Cleanup(),Clear(), Stats(), Setting()
            )
        )
    }
    fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        return Flux.fromIterable(commands)
            .filter { it.name == event.commandName }
            .next()
            .flatMap { it.handle(event) }
    }
}