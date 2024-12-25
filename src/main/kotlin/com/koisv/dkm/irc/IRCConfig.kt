package com.koisv.dkm.irc

import com.koisv.dkm.DataManager
import com.koisv.dkm.DataManager.IRC.motdFile
import com.koisv.dkm.DataManager.hash
import com.koisv.dkm.irc.IRCChannelImpl.IRCChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
@Serializable
data class IRCConfig(
    @SerialName("Server Name")
    val serverName: String,
    @SerialName("Welcome Message")
    val welcomeMsg: String,
    @SerialName("Minimum Nickname Length")
    val nickMaxLen: Int,
    @SerialName("Maximum Message Length")
    val msgMaxLen: Int
) {

    data class IRCCredentials(
        val id: String,
        val password: String
    ) {
        override fun toString(): String =
            "[Credentials: $id...$password]"

        override fun equals(other: Any?): Boolean =
            if (other is String)
                password == other.hash() || password == other
            else super.equals(other)

        override fun hashCode(): Int = id.hashCode() + password.hashCode()
    }

    @Transient
    val motd: String = motdFile.readLines().joinToString("\n")
    @Transient
    val channels: List<IRCChannel> = DataManager.IRC.loadIRCChans()
}
