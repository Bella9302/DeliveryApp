package com.itmda3b33.deliveryapp.validation

/**
 * The refund limits from the Systems Brief, section 6. All amounts are in cents.
 *
 * - Missing item worth under R15: refund 50% automatically.
 * - Worth more than R50: a human agent decides.
 * - R15 to R50 [Confirm]: the group chose to send these to an agent. Set
 *   [autoRefundMiddleBand] to true to auto-refund them instead.
 */
class RefundPolicy(
    val autoRefundBelowCents: Long = 15_00,
    val escalateAboveCents: Long = 50_00,
    val autoRefundPercent: Int = 50,
    val autoRefundMiddleBand: Boolean = false,
) {

    /** The refund our code offers for a missing item worth [valueCents]. Rounded down to the cent. */
    fun refundFor(valueCents: Long): Long = valueCents * autoRefundPercent / 100

    /** True when the system may approve the refund without a human. */
    fun canAutoRefund(valueCents: Long): Boolean = when {
        valueCents < autoRefundBelowCents -> true
        valueCents > escalateAboveCents -> false
        else -> autoRefundMiddleBand
    }
}
