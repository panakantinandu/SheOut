package com.sheout.admin.internal.web;

import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The refund rules the console shows beside the Refund button.
 * <p>
 * Refunds from the console go to her SheOut wallet. Three cases go back to
 * the card, UPI or bank account she paid with instead - a payment that failed
 * after money left her account, a double charge, and a trip SheOut cancelled
 * after she paid online - within REFUND_TO_SOURCE_WORKING_DAYS. SheOut has no
 * Razorpay refund API yet, so finance does those from the Razorpay dashboard
 * (docs/refunds-to-source.md). The Refund and Cancellation Policy promises
 * the same number; scripts/check-legal-sync.mjs keeps the two equal.
 */
@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundPolicyController {

    private final int toSourceWorkingDays;

    public AdminRefundPolicyController(@Value("${sheout.refunds.to-source-working-days}") int toSourceWorkingDays) {
        this.toSourceWorkingDays = toSourceWorkingDays;
    }

    public record RefundPolicy(int toSourceWorkingDays, String runbook) {
    }

    @RequiresPermission(Permission.REFUNDS_ISSUE)
    @GetMapping("/policy")
    public ResponseEntity<RefundPolicy> policy() {
        return ResponseEntity.ok(new RefundPolicy(toSourceWorkingDays, "docs/refunds-to-source.md"));
    }
}
