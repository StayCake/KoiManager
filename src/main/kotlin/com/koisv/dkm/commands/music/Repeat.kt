package com.koisv.dkm.commands.music

import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.data.Data
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import reactor.core.publisher.Mono

class Repeat : SlashCmd{
    override val name: String = "반복"
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        var emp = false
        val currentConf = Data.getConf(event.interaction.guildId.get())
        val response : String = when (event.options[0].name) {
            "켜기" -> {
                when (event.options[0].options[0].name) {
                    "한곡" -> {
                        if (currentConf.repeat != 2) {
                            currentConf.repeat = 2
                            "한곡 반복을 켰습니다."
                        } else {
                            emp = true
                            "이미 켜져있습니다!"
                        }
                    }
                    "전곡" -> {
                        if (currentConf.repeat != 1) {
                            currentConf.repeat = 1
                            "전곡 반복을 켰습니다."
                        } else {
                            emp = true
                            "이미 켜져있습니다!"
                        }
                    }
                    else -> "오류 발생."
                }
            }
            "끄기" -> {
                if (currentConf.repeat != 0) {
                    currentConf.repeat = 0
                    "반복을 껐습니다."
                } else {
                    emp = true
                    "이미 꺼져있습니다!"
                }
            }
            else -> "오류 발생."
        }
        return event.reply()
            .withContent(response)
            .withEphemeral(emp)
    }
}