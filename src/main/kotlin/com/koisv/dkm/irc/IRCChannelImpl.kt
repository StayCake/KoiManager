package com.koisv.dkm.irc

import com.koisv.dkm.irc.IRCException.ChannelKeyIsSetException
import com.koisv.dkm.irc.IRCMessageImpl.ServerMessage
import com.koisv.dkm.irc.IRCModeImpl.Mode
import com.koisv.dkm.irc.IRCModeImpl.ModeSet
import java.util.regex.Pattern
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object IRCChannelImpl {
    class IRCChannelManager(config: IRCConfig) {
        private var channels: HashMap<String, IRCChannel?> = hashMapOf()
        private val channelPrefixes: Array<String> = arrayOf("&", "!", "+", "#")

        init { config.channels.forEach { channels[it.name] = it } }

        fun addChannel(name: String, topic: String) { channels[name] = IRCChannel(name, topic)
        }
        fun addChannel(channel: IRCChannel) { channels[channel.name] = channel }

        fun removeChannel(name: String) { channels.remove(name) }

        fun isChannel(target: String): Boolean = isChannelType(target) && channels.containsKey(target)

        fun isValidChannelName(name: String): Boolean = when {
            !isChannelType(name) -> false
            name.length > 50 -> false
            else -> true
        }

        fun isChannelType(target: String): Boolean = channelPrefixes.any { target.startsWith(it) }

        fun getChannel(channel: String): IRCChannel? = channels[channel]

        fun getChannels(): Collection<IRCChannel?> = channels.values
        fun getChannels(onlyWith: IRCSession): ArrayList<IRCChannel> =
            ArrayList(channels.values.filterNotNull().filter { it.hasUser(onlyWith) })

        fun hasChannel(chanName: String): Boolean = channels[chanName] != null
    }

    data class IRCChannel(
        var name: String,
        var topic: String,
        override val modes: ModeSet = ModeSet()
    ): IRCModeImpl.Moddable {
        private var topicAuthor = "Server"
        private val channelUserModes = hashMapOf<IRCSession, ModeSet>()

        private val masks = hashMapOf<Mode, ArrayList<Pattern>>()
        val users = mutableSetOf<IRCSession>()

        @get:Synchronized
        @set:Synchronized
        @set:Throws(ChannelKeyIsSetException::class)
        var key: String? = null
            set(value) {
                if (field != null && value != null) throw ChannelKeyIsSetException()
                field = value
            }

        @get:Synchronized
        @set:Synchronized
        @set:Throws(IllegalArgumentException::class)
        var userLimit: Int = 10
            set(value) {
                require(value >= 1) { "User limit must be greater than 1" }
                field = value
            }

        @Synchronized
        fun addUser(user: IRCSession) { if (!users.contains(user)) users.add(user) }
        @Synchronized
        fun removeUser(user: IRCSession) {
            if (users.contains(user)) users.remove(user)
            channelUserModes.remove(user)
        }

        @Synchronized
        fun addModeForUser(user: IRCSession, mode: Mode) {
            if (hasUser(user)) {
                val ms: ModeSet = channelUserModes[user] ?: ModeSet()
                ms.addMode(mode)
                channelUserModes[user] = ms
            }
        }
        @Synchronized
        fun removeModeForUser(user: IRCSession, mode: Mode) {
            if (channelUserModes.containsKey(user)) {
                val ms: ModeSet = channelUserModes[user] ?: ModeSet()
                ms.removeMode(mode)
                channelUserModes[user] = ms
            }
        }
        @Synchronized
        fun hasModeForUser(user: IRCSession, mode: Mode): Boolean {
            return if (channelUserModes.containsKey(user))
                (channelUserModes[user] ?: return false).hasMode(mode)
            else false
        }
        @Synchronized
        fun getModesForUser(user: IRCSession): ModeSet = channelUserModes[user] ?: ModeSet()

        @Synchronized
        fun getNicks(): ArrayList<String> = ArrayList(users.map { it.client.nick })

        @Synchronized
        fun hasUser(user: IRCSession): Boolean = users.contains(user)

        @Synchronized
        fun findConnectionByNick(nick: String): IRCSession? =
            users.firstOrNull { it.client.nick == nick }

        fun hasMask(mode: Mode?, searchMask: String): Boolean =
            masks[mode ?: false]?.any { it.pattern() == searchMask } == true

        fun setTopic(topic: String, topicAuthor: String) {
            this.topic = topic
            this.topicAuthor = topicAuthor
        }

        suspend fun broadcast(serverMessage: ServerMessage, exclude: ArrayList<IRCSession> = arrayListOf()) {
            users.filter { !exclude.contains(it) }.forEach { it.send(serverMessage) }
        }

        @Synchronized
        fun addMask(mode: Mode, mask: String) {
            val masks: ArrayList<Pattern> = masks[mode] ?: arrayListOf()
            val maskPattern = Pattern.compile(mask)
            if (!masks.contains(maskPattern)) masks.add(maskPattern)
            this.masks[mode] = masks
        }

        @Synchronized
        fun getMask(mode: Mode?): ListIterator<Pattern>? {
            val masks = masks[mode] ?: return null

            return if (masks.isNotEmpty()) masks.listIterator() else null
        }

        @Synchronized
        fun removeMask(mode: Mode, searchMask: String) {
            masks[mode]?.removeIf { it.pattern() == searchMask }
        }

        @Synchronized
        fun clearMasks(mode: Mode?) { masks[mode]?.clear() }

        fun clearKey() { key = null }
    }
}