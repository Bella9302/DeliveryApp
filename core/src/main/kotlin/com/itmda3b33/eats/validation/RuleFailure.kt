package com.itmda3b33.eats.validation

/** The checks from the Systems Brief, section 6, plus two extra ones for screens outside the chat. */
enum class Check {
    OWNERSHIP,
    DELIVERY,
    DUPLICATE,
    WELL_FORMED,
    AMOUNT,

    /** The record is in a status that does not allow this change. */
    STATUS,

    /** The user's role does not allow this action. */
    PERMISSION,
}

/**
 * Why an action was refused. [code] is a stable machine-readable reason (useful for Gemini and tests),
 * [message] is a plain sentence that can be shown to the user.
 */
data class RuleFailure(
    val check: Check,
    val code: String,
    val message: String,
)

/** Runs checks in order and returns the first failure, or null when all pass. */
internal fun firstFailure(vararg checks: () -> RuleFailure?): RuleFailure? {
    for (check in checks) {
        check()?.let { return it }
    }
    return null
}
