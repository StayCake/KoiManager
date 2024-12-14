package com.koisv.dkm.irc

sealed class IRCException: Exception {
    constructor(): super()
    constructor(msg: String): super(msg)

    class ModeNotFoundException(mode: String): IRCException(mode)
    class InvalidMessageException(msg: String): IRCException(msg)
    class InvalidCommandException(msg: String): IRCException(msg)
    class InvalidModeOperationException: IRCException()
    class BadMaskException: IRCException()
    class ChannelKeyIsSetException: IRCException()
    class MissingCommandParametersException: IRCException()
    class MissingModeArgumentException: IRCException()
    class IRCActionException(val replyCode: IRCMessageImpl.ReplyCode, val replyMessage: String): IRCException()
}