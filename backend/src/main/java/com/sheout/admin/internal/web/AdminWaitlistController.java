package com.sheout.admin.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.FeatureWaitlistApi;
import com.sheout.users.WaitlistFeature;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * How many people asked to hear about each feature that does not exist yet -
 * the demand signal "Notify me" buttons exist to collect. Counts only: who
 * signed up stays in the users module.
 */
@RestController
@RequestMapping("/api/v1/admin/waitlist")
public class AdminWaitlistController {

    private final FeatureWaitlistApi waitlist;

    public AdminWaitlistController(FeatureWaitlistApi waitlist) {
        this.waitlist = waitlist;
    }

    @GetMapping
    public ResponseEntity<List<WaitlistCount>> counts() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin only");
        }
        return ResponseEntity.ok(Arrays.stream(WaitlistFeature.values())
                .map(feature -> new WaitlistCount(feature, waitlist.countInterested(feature)))
                .toList());
    }

    public record WaitlistCount(WaitlistFeature feature, long interested) {
    }
}
