package io.github.ccch1mneyyy.dsh_app.phone

sealed interface JevDecision {
    /** Goal accomplished. */
    object Done : JevDecision
    /** Cannot proceed without human help. */
    data class Blocked(val reason: String) : JevDecision
    /** Confidence below threshold — caller should fall back to DS direct control. */
    object Fallback : JevDecision
    /** Execute this tool call next. */
    data class Execute(val toolName: String, val args: Map<String, Any?>) : JevDecision
}

object JevRunner {
    /**
     * Given the current [screenText] and [goal], asks Jev which single action to take next.
     * Returns a [JevDecision] for [PhoneCore] to act on; never performs any device action itself.
     */
    fun decide(
        goal: String,
        screenText: String,
        screenId: String,
        config: JevConfig,
        history: List<String>,
    ): JevDecision {
        val elements = extractElements(screenText)
        val candidates = buildCandidates(elements, screenText)
        if (candidates.size <= 2) return JevDecision.Fallback  // Only DONE/BLOCKED — no real choices.

        val state = buildState(goal, screenText, history)
        val choice = JevClient.decide(config, state, "根据目标和当前屏幕，选择下一步操作。", candidates)
            ?: return JevDecision.Fallback

        if (choice.probability < config.confidenceThreshold || choice.margin < config.marginThreshold) {
            return JevDecision.Fallback
        }

        return when {
            choice.value == "DONE" -> JevDecision.Done
            choice.value == "BLOCKED" -> JevDecision.Blocked("Jev 判断无法继续")
            choice.value.startsWith("tap:") -> {
                val id = choice.value.removePrefix("tap:").toIntOrNull() ?: return JevDecision.Fallback
                JevDecision.Execute(PhoneToolCatalog.TAP, mapOf("screen" to screenId, "id" to id))
            }
            choice.value.startsWith("scroll:") -> {
                val rest = choice.value.removePrefix("scroll:")
                val parts = rest.split(":")
                val direction = parts.last()
                if (direction !in listOf("up", "down", "left", "right")) return JevDecision.Fallback
                val listId = if (parts.size > 1) parts[0].toIntOrNull() else null
                JevDecision.Execute(PhoneToolCatalog.SCROLL, buildMap {
                    put("screen", screenId)
                    put("direction", direction)
                    if (listId != null) put("id", listId)
                })
            }
            else -> JevDecision.Fallback
        }
    }

    private fun buildState(goal: String, screenText: String, history: List<String>): String = buildString {
        append("目标：").append(goal).append("\n\n")
        append("当前屏幕：\n").append(screenText.take(3000))
        if (history.isNotEmpty()) {
            append("\n\n已执行步骤：\n")
            history.takeLast(5).forEach { append("- ").append(it).append("\n") }
        }
    }

    /** Extracts [(id, description)] from lines like "  [5] 按钮 返回" or "[3] 列表 可下滑". */
    internal fun extractElements(screenText: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val pattern = Regex("""\[(\d+)]\s+([^\[]{1,100})""")
        for (match in pattern.findAll(screenText)) {
            val id = match.groupValues[1].toIntOrNull() ?: continue
            val text = match.groupValues[2].trim().trimEnd('·', ' ').take(80)
            if (text.isNotBlank()) result += id to text
        }
        return result
    }

    private fun buildCandidates(elements: List<Pair<Int, String>>, screenText: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for ((id, text) in elements) {
            map["tap:$id"] = "点击：$text"
        }
        // Scrollable lists
        val listPattern = Regex("""\[(\d+)]\s+(?:列表|[^\[]*可(?:下|上)滑)""")
        for (match in listPattern.findAll(screenText)) {
            val listId = match.groupValues[1]
            map["scroll:$listId:down"] = "向下滚动列表 [$listId]"
            map["scroll:$listId:up"] = "向上滚动列表 [$listId]"
        }
        if (!map.keys.any { it.startsWith("scroll:") }) {
            map["scroll::down"] = "向下滚动页面"
            map["scroll::up"] = "向上滚动页面"
        }
        map["DONE"] = "目标已完成，不需要再操作"
        map["BLOCKED"] = "无法继续，需要人工介入"
        return map
    }
}
