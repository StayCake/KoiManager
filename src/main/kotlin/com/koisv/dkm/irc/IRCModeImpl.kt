package com.koisv.dkm.irc

import com.koisv.dkm.irc.IRCChannelImpl.IRCChannel
import com.koisv.dkm.irc.IRCException.*
import com.koisv.dkm.irc.IRCModeImpl.ChannelMode.entries
import com.koisv.dkm.irc.IRCModeImpl.ModeStrategy.*
import com.koisv.dkm.irc.IRCModeImpl.UserMode.entries
import kotlinx.serialization.SerialName
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
object IRCModeImpl {
    sealed interface Mode {
        val shortFlag: String
        val reqArgs: Boolean
        val strategy: ModeStrategy

        sealed interface Parseable {
            fun get(shortFlag: String): Mode?
            fun has(modes: String): Boolean
        }

        @Suppress("CovariantEquals")
        fun equals(m: String) = m == shortFlag

        override fun toString(): String

        @Throws(IRCActionException::class)
        fun addMode(channel: IRCChannel, session: IRCSession, mode: Mode, arg: String = "") {
            val target = channel.findConnectionByNick(arg)
            when (strategy) {
                USER -> throw InvalidModeOperationException()
                MASK -> channel.addMask(mode, arg)
                CHANNEL -> channel.addMode(mode)
                CHANNEL_USER -> if (target == null) throw IRCActionException(
                    IRCMessageImpl.ReplyCode.ERR_USERNOTINCHANNEL,
                    session.client.nick + " :User not in channel"
                ) else channel.addModeForUser(target, mode)
                ARGUMENT -> when (mode) {
                    ChannelMode.CHAN_KEY -> {
                        try {
                            channel.key = arg
                        } catch (e: ChannelKeyIsSetException) {
                            throw IRCActionException(
                                IRCMessageImpl.ReplyCode.ERR_KEYSET,
                                "${session.client.nick} ${channel.name} :Error key already set"
                            )
                        }
                    }
                    ChannelMode.USER_LIMIT -> {
                        try {
                            channel.userLimit = arg.toIntOrNull() ?: throw Exception()
                        } catch (e: Exception) {
                            throw IRCActionException(
                                IRCMessageImpl.ReplyCode.ERR_BADMASK,
                                "${session.client.nick} ${channel.name} :Invalid user limit"
                            )
                        }
                    }
                    else -> return
                }
            }
        }
        @Throws(IRCActionException::class)
        fun removeMode(channel: IRCChannel, session: IRCSession, mode: Mode, arg: String = "") {
            val target = channel.findConnectionByNick(arg)
            when (strategy) {
                USER -> throw InvalidModeOperationException()
                MASK -> channel.removeMask(mode, arg)
                CHANNEL -> channel.removeMode(mode)
                CHANNEL_USER -> when {
                    target == null -> throw IRCActionException(
                        IRCMessageImpl.ReplyCode.ERR_USERNOTINCHANNEL,
                        session.client.nick + " :User not in channel"
                    )
                    channel.hasModeForUser(target, ChannelMode.OWNER) &&
                            session != target &&
                            !session.modes.hasMode(UserMode.OPERATOR) -> throw IRCActionException(
                        IRCMessageImpl.ReplyCode.ERR_NOPRIVILEGES,
                        session.client.nick + " :Can't change owner's modes"
                    )
                    else -> channel.removeModeForUser(target, mode)
                }
                ARGUMENT ->
                    if (mode == ChannelMode.CHAN_KEY) channel.clearKey()
                    else throw IRCActionException(
                        IRCMessageImpl.ReplyCode.ERR_BADMASK,
                        "${session.client.nick} ${channel.name} :Invalid user limit"
                    )
            }
        }

        @Throws(IRCActionException::class)
        fun addMode(session: IRCSession, target: IRCSession, mode: Mode) {
            if (mode !in listOf(UserMode.OPERATOR, UserMode.LOCAL_OPERATOR, UserMode.AWAY)) {
                target.modes.addMode(mode)
            }
        }
        @Throws(IRCActionException::class)
        fun removeMode(session: IRCSession, target: IRCSession, mode: Mode) {
            if (strategy != USER) throw InvalidModeOperationException()
            if (mode != UserMode.RESTRICTED && (session.modes.hasMode(UserMode.OPERATOR) || session.modes.hasMode(
                    UserMode.LOCAL_OPERATOR
                ))
            ) {
                target.modes.removeMode(mode)
            }
        }
    }

    interface Moddable {
        val modes: ModeSet

        fun addMode(mode: Mode): Boolean =
            if (!modes.set.contains(mode)) {
                modes.set.add(mode); true
            } else false

        fun removeMode(mode: Mode): Boolean =
            if (modes.set.contains(mode)) {
                modes.set.remove(mode); true
            } else false

        fun clearModes() { modes.set.clear() }

        val stringModes: String
            get() = modes.set.joinToString(separator = "") { it.shortFlag }

        fun hasMode(mode: Mode): Boolean = mode in modes.set
        fun hasMode(mode: Char): Boolean = mode in stringModes

        fun hasMode(any: String? = null, all: String? = null): Boolean = when {
            any != null -> any.any(this::hasMode)
            all != null -> all.all(this::hasMode)
            else -> false
        }

        fun hasMode(any: ModeSet? = null, all: ModeSet? = null): Boolean = when {
            any != null -> any.modes.set.any { it in modes.set }
            all != null -> all.modes.set.all { it in modes.set }
            else -> false
        }

        fun hasMode(any: Collection<Mode>? = null, all: Collection<Mode>? = null): Boolean = when {
            any != null -> any.any { it in modes.set }
            all != null -> all.all { it in modes.set }
            else -> false
        }
    }

    class ModeSet: Moddable {
        override val modes: ModeSet = this
        @SerialName("ModeSet")
        val set = mutableSetOf<Mode>()

        val size: Int = modes.set.size
        override fun toString(): String = "{IRCModeSet: $stringModes}"
    }

    enum class ModeStrategy { CHANNEL, USER, CHANNEL_USER, ARGUMENT, MASK }

    enum class UserMode(
        override val shortFlag: String,
        override val reqArgs: Boolean = false,
        override val strategy: ModeStrategy = USER
    ): Mode {
        OPERATOR("o"),
        LOCAL_OPERATOR("O"),
        AWAY("a"),
        WALLOPS("w"),
        RESTRICTED("r"),
        SNOTICE("s"),
        INVISIBLE("i");

        companion object: Mode.Parseable {
            override fun get(shortFlag: String): Mode? = entries.findLast { it.shortFlag == shortFlag }
            override fun has(modes: String): Boolean =
                entries.map { it.shortFlag.first() }.containsAll(modes.toCharArray().toSet())
        }
        override fun toString(): String = name
    }

    enum class ChannelMode(
        override val shortFlag: String,
        override val strategy: ModeStrategy = CHANNEL,
        override val reqArgs: Boolean = false
    ): Mode {
        CHAN_OPERATOR("o", CHANNEL_USER, true),
        VOICE("v", CHANNEL_USER, true),
        OWNER("O", CHANNEL_USER, true),
        //ANONYMOUS("a"),
        INVITE("i"),
        MODERATED("m"),
        CHAN_SRC_ONLY("n"),
        QUIET("q"),
        PRIVATE("p"),
        SECRET("s"),
        REOP("r"),
        OP_TOPIC_ONLY("t"),
        CHAN_KEY("k", ARGUMENT, true),
        USER_LIMIT("l", ARGUMENT),
        BAN_MASK("b", MASK),
        BAN_MASK_EXCEPTION("e", MASK),
        INVITATION_MASK("I", MASK, true);

        companion object: Mode.Parseable {
            override fun get(shortFlag: String): Mode? = entries.findLast { it.shortFlag == shortFlag }
            override fun has(modes: String): Boolean =
                entries.map { it.shortFlag.first() }.containsAll(modes.toCharArray().toSet())
        }
        override fun toString(): String = name
    }
}