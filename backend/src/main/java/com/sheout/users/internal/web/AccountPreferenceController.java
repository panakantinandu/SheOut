package com.sheout.users.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.AppLanguage;
import com.sheout.users.WaitlistFeature;
import com.sheout.users.internal.AccountPreferenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;

/**
 * The signed-in account's own preferences and waitlist places, riders and
 * partners alike. Scoped entirely by the token: no path carries an account
 * id, so there is nothing to enumerate and nothing of anyone else's to reach.
 */
@RestController
public class AccountPreferenceController {

    private final AccountPreferenceService service;

    public AccountPreferenceController(AccountPreferenceService service) {
        this.service = service;
    }

    /** language is null until she has chosen one - the app then follows the phone. */
    @GetMapping("/api/v1/users/me/preferences")
    public ResponseEntity<Preferences> myPreferences() {
        CurrentAccount caller = requireAuthenticated();
        return ResponseEntity.ok(new Preferences(service.findLanguage(caller.accountId()).map(AppLanguage::code).orElse(null)));
    }

    @PutMapping("/api/v1/users/me/preferences/language")
    public ResponseEntity<Preferences> setLanguage(@Valid @RequestBody LanguageRequest request) {
        CurrentAccount caller = requireAuthenticated();
        AppLanguage language = AppLanguage.fromCode(request.language())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LANGUAGE",
                        "Choose one of: " + String.join(", ", Arrays.stream(AppLanguage.values()).map(AppLanguage::code).toList())));
        return ResponseEntity.ok(new Preferences(service.setLanguage(caller.accountId(), language).code()));
    }

    @GetMapping("/api/v1/users/me/waitlist/{feature}")
    public ResponseEntity<WaitlistStatus> waitlistStatus(@PathVariable String feature) {
        CurrentAccount caller = requireAuthenticated();
        WaitlistFeature which = parseFeature(feature);
        Instant joinedAt = service.joinedAt(caller.accountId(), which).orElse(null);
        return ResponseEntity.ok(new WaitlistStatus(which, joinedAt != null, joinedAt));
    }

    /** Idempotent - tapping twice is one sign-up. */
    @PostMapping("/api/v1/users/me/waitlist/{feature}")
    public ResponseEntity<WaitlistStatus> joinWaitlist(@PathVariable String feature) {
        CurrentAccount caller = requireAuthenticated();
        WaitlistFeature which = parseFeature(feature);
        return ResponseEntity.ok(new WaitlistStatus(which, true, service.join(caller.accountId(), which)));
    }

    public record Preferences(String language) {
    }

    public record LanguageRequest(@NotBlank String language) {
    }

    public record WaitlistStatus(WaitlistFeature feature, boolean joined, Instant joinedAt) {
    }

    /** An unknown feature is a 404 like any other path that does not exist. */
    private static WaitlistFeature parseFeature(String feature) {
        try {
            return WaitlistFeature.valueOf(feature.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("No such feature");
        }
    }

    private static CurrentAccount requireAuthenticated() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }
}
