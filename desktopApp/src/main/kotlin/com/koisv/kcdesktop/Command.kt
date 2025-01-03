package com.koisv.kcdesktop

object Command {
    /**
     * 커멘드 사용법 목록
     */
    val commandUsages =
        mapOf<List<String>, String>(
            listOf("/w", "/msg") to "<사용자> : 특정 사용자에게 귓속말을 보냅니다.",
            listOf("/r") to ": 마지막으로 귓속말을 보낸 사용자에게 답장을 보냅니다.",
            listOf("/nick") to "<닉네임> : 사용자의 닉네임을 변경합니다.",
            listOf("/logout") to ": 로그아웃합니다.",
        )

    /**
     * 커멘드 사용법을 반환합니다.
     * @param command 커멘드
     * @return 사용법
     */
    fun getCommandUsage(command: String): String? {
        val usage = commandUsages.map {
                val key = it.key.firstOrNull { it.startsWith(command) }
                if (key != null) key + " " + it.value else null
            }.filterNotNull()
        if (usage.isNotEmpty()) return usage.joinToString("\n")
        return null
    }

    /**
     * 커멘드가 유효한지 확인합니다.
     * @param String 커멘드
     * @return 유효 여부
     */
    val String.isValidCommand get() = commandUsages.keys.any { it.contains(this) }
}