package com.sheout.admin.internal;

import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Creates the first ADMIN. Signup refuses role=ADMIN (see AuthController's
 * requireSelfServiceRole), so without this there is no legitimate way to
 * reach any admin endpoint - the driver-verification and SOS admin
 * endpoints that already existed were only usable against a row flipped by
 * hand in the database.
 * <p>
 * Blank by default and a no-op when unset - the same opt-in treatment
 * Razorpay/Twilio/dev-OTP config gets, and the reason this is safe to ship
 * enabled-by-nothing: an environment that never sets the variable never
 * gains an admin. Deliberately keyed on a phone number of an account that
 * must ALREADY exist (sign in normally first, then get promoted) rather
 * than creating an account outright, so this can never mint a login out of
 * thin air - it only widens an identity a human already controls.
 * <p>
 * Runs on every boot and is idempotent. It is NOT a one-shot migration on
 * purpose: the variable is the durable statement of who operates this
 * deployment, so a restored database or a fresh environment converges to
 * the same answer without anyone remembering to re-run something.
 */
@Component
class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthApi authApi;
    private final String bootstrapPhone;

    AdminBootstrap(AuthApi authApi, @Value("${sheout.admin.bootstrap-phone:}") String bootstrapPhone) {
        this.authApi = authApi;
        this.bootstrapPhone = bootstrapPhone;
    }

    @EventListener(ApplicationReadyEvent.class)
    void promoteConfiguredAdmin() {
        if (bootstrapPhone.isBlank()) {
            return;
        }
        Optional<AccountSummary> promoted = authApi.grantAdminRole(bootstrapPhone);
        if (promoted.isEmpty()) {
            // Not fatal: the operator may simply not have signed in yet. Next
            // boot picks it up, so this warns rather than failing startup.
            log.warn("Admin bootstrap: no account exists for the configured phone number yet - "
                    + "sign in once with it, then restart to complete promotion");
            return;
        }
        log.info("Admin bootstrap: account {} holds role {}", promoted.get().id(), promoted.get().role());
    }
}
