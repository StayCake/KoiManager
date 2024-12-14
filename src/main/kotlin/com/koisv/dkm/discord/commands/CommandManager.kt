package com.koisv.dkm.discord.commands

import com.koisv.dkm.discord.KoiManager.Companion.dSysG
import com.koisv.dkm.discord.data.Bot
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.Kord
import dev.kord.rest.builder.interaction.*
import kotlinx.coroutines.isActive

object CommandManager {
    suspend fun globalReg(botData: Bot, kord: Kord) {
        if (!kord.isActive) return
        kord.createGlobalChatInputCommand(
            "재생",
            "노래를 재생합니다.",
        ) {
            string("링크","재생할 노래의 링크를 적어주세요.") {
                required = false
            }
            dmPermission = false
        }
        kord.createGlobalChatInputCommand(
            "청소",
            "채팅을 청소합니다."
        ) {
            integer("청소량", "청소할 갯수를 입력해주세요 [기본, 최대 100개]") {
                required = false
                minValue = 0
                maxValue = 100
            }
            dmPermission = false
        }
        kord.createGlobalChatInputCommand(
            "설정", "봇 설정"
        ) {
            subCommand("채널", "안내 채널 설정") {
                string("유형", "지정할 채널 유형") {
                    choice("패치노트", "update") {}
                    choice("공지사항", "default") {}
                    required = true
                }
                channel("대상", "지정할 채널") {
                    required = false
                    channelTypes = listOf(ChannelType.GuildText, ChannelType.GuildNews)
                }
            }
            dmPermission = false
        }

        val debugId = botData.debugGuild
        if (botData.isTest) {
            kord.createGlobalChatInputCommand("dbg", "개발자 전용 명령어 입니다. [사용금지]") {
                integer("key", "The Key Code") {
                    minValue = 100000
                    maxValue = 999999
                }
            }
        } else if (debugId != null) {
            kord.createGuildChatInputCommand(debugId, "dbg", "개발자 전용 명령어 입니다. [사용금지]") {
                integer("key", "The Key Code") {
                    minValue = 100000
                    maxValue = 999999
                }
                defaultMemberPermissions = Permissions() + Permission.UseApplicationCommands
            }
        }
    }
    suspend fun globalCleanUp(kord: Kord) = kord.getGlobalApplicationCommands().collect { it.delete() }
    suspend fun debugReload(kord: Kord) {
        kord.createGuildChatInputCommand(
            dSysG.id, "bmu", "Debug Utility"
        ) {
            group("guild", "Bot Own Guild") {
                subCommand("create","Create Bot Own Guild") {}
                subCommand("delete", "Delete Bot Own Guild") {}
                subCommand("grant", "Grant All permissions") {}
            }
            group("command", "Command Management") {
                subCommand("cleanup", "Cleanup Commands") {}
                subCommand("guildclean", "Guild Cleanup") {
                    string("id", "Target ID") {
                        required = true
                    }
                }
                subCommand("reload", "Command Register Again") {}
            }
            group("system", "System Management") {
                subCommand("shutdown", "Shutdown System") {}
                subCommand("save", "Save All Data") {}
                subCommand("report", "Print Out Report") {}
                subCommand("close", "Close Debug Commands") {}
            }
            group("notice", "Send Notice") {
                subCommand("create", "Create Form") {
                    string("title", "Form Title") {}
                }
                subCommand("add", "Add New Field") {
                    string("name", "Field Title") { required = true }
                    string("value", "Field Value") { required = true }
                }

                subCommand("send", "Send this") {
                    string("location", "Send location") {
                        choice("공지사항", "notice") {}
                        choice("패치노트", "update") {}
                        required = true
                    }
                    boolean("enforce", "Ignore Channel Set") {}
                }
            }
            subCommand("test", "Test Command") {}
        }
    }
}