package com.itmda3b33.eats.validation

/** What to do with a missing-item claim between the two limits (R15 to R50). [Confirm] with the group. */
enum class MidRangeDecision {
    AUTO_REFUND,
    ESCALATE,
}

/**
 * Refund limits from the Systems Brief, section 6. All amounts are in cents.
 *
 * - Claim value under [autoRefundBelowCents]: refund [autoRefundPercent] automatically.
 * - Claim value above [escalateAboveCents]: escalate to an agent.
 * - In between: follow [midRangeDecision]. The group has not decided yet, so the default
 *   is the safe choice: a person looks at it.
 *
 * If these limits change, update the matching limit in firestore.rules as well.
 */
data class RefundPolicy(
    val autoRefundBelowCents: Long = 1_500,
    val autoRefundPercent: Int = 50,
    val escalateAboveCents: Long = 5_000,
    val midRangeDecision: MidRangeDecision = MidRangeDecision.ESCALATE,
) {
    init {
        require(autoRefundPercent in 1..100) { "autoRefundPercent must be 1 to 100" }
        require(autoRefundBelowCents <= escalateAboveCents) { "limits overlap" }
    }

    /** Percentage of [claimValueCents], rounded half up to the nearest cent. */
    fun autoRefundAmount(claimValueCents: Long): Long =
        (claimValueCents * autoRefundPercent + 50) / 100

    companion object {
        val DEFAULT = RefundPolicy()
    }
}
