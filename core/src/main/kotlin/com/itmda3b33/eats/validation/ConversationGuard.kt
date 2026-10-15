package com.itmda3b33.eats.validation

/**
 * Makes sure a customer never gets stuck in a loop with the bot.
 *
 * - [isHumanRequest] spots "I want a human" so the app can escalate at once, without asking Gemini.
 * - [record] counts turns that went nowhere. After [maxStuckTurns] in a row, [shouldEscalate] becomes true.
 */
class ConversationGuard(private val maxStuckTurns: Int = 2) {

    private var stuckTurns = 0

    /** Call after every bot turn. A turn is stuck when an action was blocked or failed, or the bot itself errored. */
    fun record(outcomes: List<ActionOutcome>, botFailed: Boolean) {
        val madeProgress = outcomes.any { it is ActionOutcome.Done || it is ActionOutcome.Escalated }
        val stuck = botFailed || outcomes.any { it is ActionOutcome.Blocked || it is ActionOutcome.Failed }
        stuckTurns = when {
            madeProgress && !stuck -> 0
            stuck -> stuckTurns + 1
            else -> stuckTurns
        }
    }

    val shouldEscalate: Boolean get() = stuckTurns >= maxStuckTurns

    fun reset() {
        stuckTurns = 0
    }

    companion object {
        private val humanPhrases = listOf(
            "human", "real person", "a person", "agent", "someone real", "speak to someone",
            "talk to someone", "call me", "operator", "manager", "representative", "customer service",
        )

        /** True when the customer is asking for a person. Kept broad on purpose: a false positive only means a person helps. */
        fun isHumanRequest(text: String): Boolean {
            val lower = text.lowercase()
            return humanPhrases.any { it in lower }
        }
    }
}
