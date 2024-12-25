package com.koisv.dkm.discord.data

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.entity.optional.value
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Embed
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Convert {

    fun Int.getEnglishNumber() : String{
        return when (this) {
            1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; else -> "X" }
    }

    fun AudioTrack.getTimeStamp() : String {
        return duration.toDuration(DurationUnit.MILLISECONDS)
            .toComponents { days, hours, minutes, seconds, _ ->
                when (days) {
                    0L ->
                        if (hours == 0) String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        else String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                    else ->
                        String.format(Locale.getDefault(), "%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
                }
            }
    }

    private fun receiveWork(embedBuilder: EmbedBuilder, recvEmbed: Embed) {
        val recvAuthor = recvEmbed.author
        if (recvAuthor != null) embedBuilder.author {
            name = recvAuthor.name
            icon = recvAuthor.iconUrl
            url = recvAuthor.url
        }
        val recvFooter = recvEmbed.footer
        if (recvFooter != null) embedBuilder.footer {
            text = recvFooter.text
            icon = recvFooter.iconUrl
        }
    }

    suspend fun TextChannel.createEmbed(embed: Embed) {
        createEmbed {
            title = embed.title ?: "ERROR"
            description = embed.description
            timestamp = embed.timestamp
            embed.fields.forEach {
                field {
                    name = it.data.name
                    value = it.data.value
                    inline = it.data.inline.value == true
                }
            }
            receiveWork(this, embed)
        }
    }

    suspend fun Message.edit(originEmbed: Embed, fieldData: List<String>) {
        edit {
            embed {
                title = originEmbed.title ?: "ERROR"
                description = originEmbed.description
                originEmbed.fields.forEach {
                    fields.add(EmbedBuilder.Field().apply {
                        name = it.name
                        value = it.value
                        inline = it.inline
                    })
                }
                fields.add(EmbedBuilder.Field().apply {
                    name = fieldData[0]
                    value = fieldData[1]
                    inline = false
                })
                timestamp = originEmbed.timestamp
                receiveWork(this, originEmbed)
            }
        }
    }
}