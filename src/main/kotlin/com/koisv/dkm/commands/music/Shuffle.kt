package com.koisv.dkm.commands.music

import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.data.Data
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

class Shuffle : SlashCmd {
    override val name: String = "셔플"
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        val guildConf = Data.getConf(event.interaction.guildId.get())
        guildConf.shuffle = guildConf.shuffle.not()
        val response = if (guildConf.shuffle) "셔플을 켰습니다." else "셔플을 껐습니다."
        return event.reply()
            .withContent(response)
            .withEphemeral(false)
    }
}