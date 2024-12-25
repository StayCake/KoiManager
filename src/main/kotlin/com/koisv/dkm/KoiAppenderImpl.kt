package com.koisv.dkm

import com.koisv.dkm.Main.loggerGui
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Node
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory
import org.apache.logging.log4j.core.layout.AbstractStringLayout
import org.apache.logging.log4j.core.layout.PatternLayout
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.io.encoding.ExperimentalEncodingApi

@Plugin(name = "KoiAppender", category = Node.CATEGORY, printObject = true)
class KoiAppenderImpl private constructor(
    name: String,
    filter: Filter?
) : AbstractAppender(name, filter, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY) {

    @OptIn(ExperimentalEncodingApi::class)
    override fun append(event: LogEvent) {
        if (event.message == null) return
        val level = event.level.toString()

        val timeMill = event.timeMillis
        val df: DateFormat = SimpleDateFormat("yy-MM-dd | HH:mm:ss", Locale.getDefault())
        val timestamp = df.format(timeMill)

        val msg = event.message.formattedMessage
        val throwable = event.thrown
        val finalString = "[$level | $timestamp] > $msg${if (msg.endsWith("\n")) "" else "\n"}"
        val stackTrace = throwable
            ?.stackTraceToString()
            ?.let { "[$level | $timestamp] > $it${if (it.endsWith("\n")) "" else "\n"}" }

        val appendArea = when (name) {
            "KM-DBot" -> loggerGui.discordLog
            "IRC" -> loggerGui.ircLog
            "KTor" -> loggerGui.ktor_MLog
            else -> loggerGui.todo4
        }

        appendArea.append(finalString)
        appendArea.caretPosition = appendArea.document.length
        stackTrace?.let { appendArea.append(finalString) }
    }

    companion object {
        class Builder<B : Builder<B>?> : AbstractStringLayout.Builder<B>(),
            org.apache.logging.log4j.core.util.Builder<KoiAppenderImpl?> {
            @PluginBuilderAttribute
            private var name: String = "null"

            override fun build(): KoiAppenderImpl = KoiAppenderImpl(name, filter = null)

            fun setName(name: String): B? {
                this.name = name
                return asBuilder()
            }
        }

        @PluginBuilderFactory
        @JvmStatic fun <B : Builder<B>?> newBuilder(): B? = Builder<B>().asBuilder()
    }
}