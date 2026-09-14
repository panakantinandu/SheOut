package com.sheout.admin.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.content.ContentApi;
import com.sheout.content.ContentBlock;
import com.sheout.content.ContentError;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The console's Content section. ADMIN only - a 403 role gate on the whole
 * surface, the same reasoning AdminController records. An unknown key is a
 * 404.
 * <p>
 * No confirmation modal in the console for a save, deliberately: an edit is
 * reversible by editing it back, every save records who made it, and a
 * version check stops one operator overwriting another. A modal on every
 * wording change would be clicked through unread, which is worse than none.
 */
@RestController
@RequestMapping("/api/v1/admin/content")
public class AdminContentController {

    private final ContentApi contentApi;
    private final AuthApi authApi;

    public AdminContentController(ContentApi contentApi, AuthApi authApi) {
        this.contentApi = contentApi;
        this.authApi = authApi;
    }

    /** Every editable block, ordered by key, so the console can group by the first segment. */
    @GetMapping
    public ResponseEntity<List<ContentRow>> all() {
        requireAdmin();
        return ResponseEntity.ok(contentApi.getContentByPrefix("").stream().map(this::toRow).toList());
    }

    @PutMapping("/{key}")
    public ResponseEntity<ContentRow> update(@PathVariable String key, @Valid @RequestBody UpdateRequest request) {
        CurrentAccount admin = requireAdmin();
        Result<ContentBlock, ContentError> result =
                contentApi.updateContent(key, request.value(), request.version(), admin.accountId());
        if (result.isFailure()) {
            throw switch (result.error()) {
                case NOT_FOUND -> ApiException.notFound("No such content");
                case STALE_VERSION -> new ApiException(HttpStatus.CONFLICT, "STALE_VERSION",
                        "Someone else saved this text after you opened it. Reload to see their version, then edit again.");
                case INVALID_VALUE -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VALUE",
                        "The text cannot be empty or longer than 2,000 characters.");
            };
        }
        return ResponseEntity.ok(toRow(result.value()));
    }

    private ContentRow toRow(ContentBlock b) {
        return new ContentRow(b.key(), b.value(), b.description(), b.version(), b.updatedAt(), b.updatedBy() == null
                ? null
                : authApi.findAccount(b.updatedBy()).map(AccountSummary::phoneNumber).orElse(null));
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    public record UpdateRequest(@NotBlank @Size(max = 2000) String value, @NotNull Long version) {
    }

    /** updatedByPhone is null while the block still holds its seeded text. */
    public record ContentRow(String key, String value, String description, long version, Instant updatedAt,
                             String updatedByPhone) {
    }
}
