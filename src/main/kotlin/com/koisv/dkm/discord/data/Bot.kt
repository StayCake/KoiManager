package com.koisv.dkm.discord.data

import dev.kord.common.entity.Snowflake


data class Bot(
    val token: Tokens = Tokens(""),
    var type: Type = Type.MAIN,
    var presence: String = "아무것도 안",
    var debugGuild: Snowflake? = null
    ) {
    enum class Type { TEST, MAIN }

    val isTest = type == Type.TEST


    data class Tokens(val discord: String, val youtube: String = "")
}
