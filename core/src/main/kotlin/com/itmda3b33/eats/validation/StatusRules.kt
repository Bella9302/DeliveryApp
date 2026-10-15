package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.RefundStatus
import com.itmda3b33.eats.data.model.Role

/** Which status changes are allowed, so records cannot jump to an impossible state. */
object StatusRules {

    private val complaintMoves: Map<ComplaintStatus, Set<ComplaintStatus>> = mapOf(
        ComplaintStatus.LOGGED to setOf(ComplaintStatus.IN_PROGRESS, ComplaintStatus.ESCALATED, ComplaintStatus.RESOLVED),
        ComplaintStatus.IN_PROGRESS to setOf(ComplaintStatus.ESCALATED, ComplaintStatus.RESOLVED),
        ComplaintStatus.ESCALATED to setOf(ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED),
        // A customer who disputes the result is escalated again.
        ComplaintStatus.RESOLVED to setOf(ComplaintStatus.ESCALATED),
    )

    private val refundMoves: Map<RefundStatus, Set<RefundStatus>> = mapOf(
        RefundStatus.REQUESTED to setOf(RefundStatus.PROCESSING, RefundStatus.APPROVED, RefundStatus.REJECTED),
        RefundStatus.PROCESSING to setOf(RefundStatus.APPROVED, RefundStatus.REJECTED),
        RefundStatus.APPROVED to setOf(RefundStatus.COMPLETED),
        RefundStatus.COMPLETED to emptySet(),
        RefundStatus.REJECTED to emptySet(),
    )

    fun canMove(from: ComplaintStatus, to: ComplaintStatus): Boolean = to in complaintMoves.getValue(from)

    fun canMove(from: RefundStatus, to: RefundStatus): Boolean = to in refundMoves.getValue(from)

    /** Agent and admin actions on a complaint, for example taking it or resolving it. */
    fun validateStaffComplaintMove(role: Role, from: ComplaintStatus, to: ComplaintStatus): RuleFailure? =
        firstFailure(
            { staffOnly(role) },
            {
                if (!canMove(from, to)) {
                    RuleFailure(Check.STATUS, "bad_transition", "A ${from.label.lowercase()} complaint cannot be moved to ${to.label.lowercase()}.")
                } else null
            },
        )

    /** Agent and admin decisions on a refund: approve, reject or mark completed. */
    fun validateStaffRefundMove(role: Role, from: RefundStatus, to: RefundStatus): RuleFailure? =
        firstFailure(
            { staffOnly(role) },
            {
                if (!canMove(from, to)) {
                    RuleFailure(Check.STATUS, "bad_transition", "A ${from.label.lowercase()} refund cannot be moved to ${to.label.lowercase()}.")
                } else null
            },
        )

    private fun staffOnly(role: Role): RuleFailure? =
        if (!role.isStaff) RuleFailure(Check.PERMISSION, "staff_only", "Only agents and admins can do this.") else null
}
