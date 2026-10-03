package com.sheout.admin.internal.web;

import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.StaffContext;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.marketplace.MarketplaceAdminApi;
import com.sheout.marketplace.MarketplaceError;
import com.sheout.marketplace.MarketplaceViews.ProductView;
import com.sheout.marketplace.MarketplaceViews.SellerAdminRow;
import com.sheout.marketplace.SellerStatus;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The console's Sellers section - the same review-queue shape as ID
 * verification: a list waiting for a person, the evidence (every product
 * and photo) in one place, and a decision with a reason. Reviewing costs the
 * seller nothing; approval is what asks her for the listing fee.
 */
@RestController
@RequestMapping("/api/v1/admin/sellers")
public class AdminSellerController {

    private final MarketplaceAdminApi marketplace;
    private final AuthApi auth;

    public AdminSellerController(MarketplaceAdminApi marketplace, AuthApi auth) {
        this.marketplace = marketplace;
        this.auth = auth;
    }

    /** A seller row with whose account it is: the number she signed in with, and whether it is blocked. */
    public record SellerRow(SellerAdminRow seller, String ownerPhone, boolean ownerBlocked) {
    }

    public record SellerDetail(SellerRow row, List<ProductView> products) {
    }

    public record ReasonRequest(String reason) {
    }

    @RequiresPermission(Permission.MARKETPLACE_MODERATE)
    @GetMapping
    public ResponseEntity<PageResponse<SellerRow>> list(@RequestParam(required = false) SellerStatus status,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer pageSize) {
        PageRequest pageable = PageRequest.of(PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize));
        return ResponseEntity.ok(PageResponse.from(marketplace.listSellers(status, q, pageable), this::withOwner));
    }

    @RequiresPermission(Permission.MARKETPLACE_MODERATE)
    @GetMapping("/awaiting-count")
    public ResponseEntity<Long> awaitingCount() {
        return ResponseEntity.ok(marketplace.countAwaitingReview());
    }

    @RequiresPermission(Permission.MARKETPLACE_MODERATE)
    @GetMapping("/{sellerId}")
    public ResponseEntity<SellerDetail> detail(@PathVariable UUID sellerId) {
        return marketplace.sellerDetail(sellerId)
                .map(d -> ResponseEntity.ok(new SellerDetail(withOwner(d.seller()), d.products())))
                .orElseThrow(() -> ApiException.notFound("No such seller"));
    }

    @RequiresPermission(Permission.MARKETPLACE_MODERATE)
    @PostMapping("/{sellerId}/approve")
    public ResponseEntity<SellerRow> approve(@PathVariable UUID sellerId) {
        CurrentAccount admin = caller();
        return ResponseEntity.ok(withOwner(unwrap(marketplace.approve(sellerId, admin.accountId()))));
    }

    @RequiresPermission(Permission.MARKETPLACE_MODERATE)
    @PostMapping("/{sellerId}/reject")
    public ResponseEntity<SellerRow> reject(@PathVariable UUID sellerId, @RequestBody ReasonRequest request) {
        CurrentAccount admin = caller();
        return ResponseEntity.ok(withOwner(unwrap(marketplace.reject(sellerId, admin.accountId(), reasonOf(request)))));
    }

    @RequiresPermission(Permission.MARKETPLACE_MODERATE)
    @PostMapping("/{sellerId}/suspend")
    public ResponseEntity<SellerRow> suspend(@PathVariable UUID sellerId, @RequestBody ReasonRequest request) {
        CurrentAccount admin = caller();
        return ResponseEntity.ok(withOwner(unwrap(marketplace.suspend(sellerId, admin.accountId(), reasonOf(request)))));
    }

    @RequiresPermission(Permission.MARKETPLACE_MODERATE)
    @PostMapping("/{sellerId}/reinstate")
    public ResponseEntity<SellerRow> reinstate(@PathVariable UUID sellerId) {
        CurrentAccount admin = caller();
        return ResponseEntity.ok(withOwner(unwrap(marketplace.reinstate(sellerId, admin.accountId()))));
    }

    private SellerRow withOwner(SellerAdminRow row) {
        AccountSummary owner = auth.findAccount(row.accountId()).orElse(null);
        return new SellerRow(row, owner == null ? null : owner.phoneNumber(), owner != null && owner.blocked());
    }

    private static String reasonOf(ReasonRequest request) {
        String reason = request == null ? null : request.reason();
        if (reason != null && reason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_TOO_LONG", "Keep the reason under 500 characters.");
        }
        return reason;
    }

    private static <T> T unwrap(Result<T, MarketplaceError> result) {
        if (result.isSuccess()) {
            return result.value();
        }
        throw switch (result.error()) {
            case SELLER_NOT_FOUND -> ApiException.notFound("No such seller");
            case REASON_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                    "Say why. The seller is shown this reason, and it is kept as the record of the decision.");
            case INVALID_TRANSITION -> new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION",
                    "This seller is not in a state that allows that. Refresh to see where they are now.");
            default -> new ApiException(HttpStatus.CONFLICT, result.error().name(), "That could not be done.");
        };
    }

    /**
     * Who is acting, for the records that say who decided. Whether she may is
     * already settled: the endpoint's permission was checked before it ran
     * (staff's StaffPermissionInterceptor).
     */
    private static CurrentAccount caller() {
        return CurrentAccountContext.get().orElseThrow(StaffContext::signInRequired);
    }
}
