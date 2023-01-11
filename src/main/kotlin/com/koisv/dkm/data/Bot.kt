package com.koisv.dkm.data

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable

@Serializable
data class Bot(
    val token: String,
    var type: Type = Type.MAIN,
    var presence: String = "아무것도 안",
    var debugGuild: Snowflake? = null
    ) {
    enum class Type { TEST, MAIN }

    val isTest = type == Type.TEST
}
