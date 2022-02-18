package com.koisv.dkm.commands.utils

import com.koisv.dkm.commands.SlashCmd
import com.koisv.dkm.data.Convert
import com.koisv.dkm.data.Data
import com.koisv.dkm.lastUp
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.spec.EmbedCreateFields.Author
import discord4j.core.spec.EmbedCreateFields.Field
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageEditSpec
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToInt

class Stats : SlashCmd {
    override val name: String = "정보"

    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        val selfUser = event.client.self.block() ?: return event.reply()
            .withEphemeral(true)
            .withContent("봇 정보 불러오기에 문제가 있습니다.")
        val thisRuntime = Runtime.getRuntime()
        val uptime = Convert.timeStamp(((System.currentTimeMillis() - lastUp) / 1000).toDouble().roundToInt())
        val embedAuthor = Author.of("Made by StayCake [NHS#1004]",null,null)
        var memberCount = 0
        event.client.guilds.toStream().forEach { memberCount += it.memberCount }
        val embed = EmbedCreateSpec.create()
            .withTitle(selfUser.username ?: "[이름 오류]")
            .withFields(
                Field.of("버전", Data.version,true),
                Field.of("유저","${memberCount}명",true),
                Field.of("서버", "${event.client.guilds.toStream().count()}개",true),
                Field.of("API","로딩 중...",true),
                Field.of("메시지","로딩 중...",true),
                Field.of("업타임",uptime,true),
                Field.of("메모리","```${
                    ((thisRuntime.totalMemory() - thisRuntime.freeMemory())/(1024.0.pow(2) / 100)).roundToInt().toDouble() / 100
                } / ${
                    (thisRuntime.maxMemory()/(1024.0.pow(2) / 100)).roundToInt().toDouble() / 100
                } MB```",false),
                Field.of("OS", "```${System.getProperty("os.name")}```",false),
                Field.of("JVM","```JDK ${Runtime.version()} | Kotlin ${KotlinVersion.CURRENT}```",false)
            )
            .withAuthor(embedAuthor)
        event.reply()
            .withEmbeds(embed)
            .withEphemeral(false).block()
        val getPing = mutableListOf<Field>()
        val ping = event.client.getGatewayClient(0).get().responseTime.toMillis()

        val now = Instant.now().toEpochMilli()
        val ms = event.reply.block()?.timestamp?.toEpochMilli() ?: now
        val current = now - ms
        embed.fields().forEach { field ->
            when (embed.fields().indexOf(field)) {
                3 -> getPing.add(Field.of("API", "$ping ms", true))
                4 -> getPing.add(Field.of("메시지", "$current ms", true))
                else -> getPing.add(embed.fields()[embed.fields().indexOf(field)])
            }
        }
        event.reply.block()?.edit(
            MessageEditSpec.create().withEmbeds(
                EmbedCreateSpec.create()
                    .withTitle(embed.title())
                    .withFields(getPing)
                    .withAuthor(embedAuthor)
            )
        )?.block()
        return Mono.empty()
    }
}