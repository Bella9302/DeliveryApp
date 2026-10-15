package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.ComplaintRepository
import com.itmda3b33.eats.data.RefundRepository
import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.MessageSender
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.RefundStatus
import com.itmda3b33.eats.data.model.TranscriptLine
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.data.model.formatRand

/** Agent and admin actions on escalated complaints and refund requests. */
class StaffService(
    private val complaints: ComplaintRepository,
    private val refunds: RefundRepository,
) {

    /** The agent takes the complaint: it moves to in progress and is assigned to them. */
    suspend fun take(staff: UserProfile, complaint: Complaint): ServiceResult<Unit> = guarded {
        StatusRules.validateStaffComplaintMove(staff.role, complaint.status, ComplaintStatus.IN_PROGRESS)
            ?.let { return@guarded ServiceResult.Invalid(it) }
        complaints.updateStatus(complaint.id, ComplaintStatus.IN_PROGRESS, assignedAgentId = staff.uid)
        note(complaint.id, "${staff.displayName} is now handling this complaint.")
        ServiceResult.Success(Unit)
    }

    suspend fun resolve(staff: UserProfile, complaint: Complaint, resolution: String): ServiceResult<Unit> = guarded {
        StatusRules.validateStaffComplaintMove(staff.role, complaint.status, ComplaintStatus.RESOLVED)
            ?.let { return@guarded ServiceResult.Invalid(it) }
        if (resolution.trim().length < ComplaintRules.MIN_DESCRIPTION) {
            return@guarded ServiceResult.Invalid(RuleFailure(Check.WELL_FORMED, "missing_resolution", "Describe how the complaint was resolved."))
        }
        complaints.updateStatus(complaint.id, ComplaintStatus.RESOLVED, assignedAgentId = staff.uid, resolution = resolution.trim())
        note(complaint.id, "Resolved: ${resolution.trim()}")
        ServiceResult.Success(Unit)
    }

    /** Approving above the auto-approve limit: the agent's ID is recorded as approvedBy. */
    suspend fun approveRefund(staff: UserProfile, refund: Refund): ServiceResult<Unit> =
        moveRefund(staff, refund, RefundStatus.APPROVED, "Refund of ${formatRand(refund.amountCents)} for ${refund.itemName} approved.")

    suspend fun rejectRefund(staff: UserProfile, refund: Refund): ServiceResult<Unit> =
        moveRefund(staff, refund, RefundStatus.REJECTED, "Refund for ${refund.itemName} was not approved.")

    /** Marks an approved refund as paid out. No real payment happens (out of scope). */
    suspend fun completeRefund(staff: UserProfile, refund: Refund): ServiceResult<Unit> =
        moveRefund(staff, refund, RefundStatus.COMPLETED, "Refund of ${formatRand(refund.amountCents)} completed.")

    private suspend fun moveRefund(staff: UserProfile, refund: Refund, to: RefundStatus, message: String): ServiceResult<Unit> = guarded {
        StatusRules.validateStaffRefundMove(staff.role, refund.status, to)?.let { return@guarded ServiceResult.Invalid(it) }
        val approvedBy = if (to == RefundStatus.APPROVED) staff.uid else null
        refunds.updateStatus(refund.id, to, approvedBy)
        note(refund.complaintId, message)
        ServiceResult.Success(Unit)
    }

    private suspend fun note(complaintId: String, text: String) {
        complaints.appendMessages(complaintId, listOf(TranscriptLine(MessageSender.SYSTEM, text)))
    }
}
