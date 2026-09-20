package com.sheout.auth.internal.web;

import com.sheout.auth.AccountSession;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.auth.SessionRevocation;
import com.sheout.auth.internal.SessionService;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Her own sign-ins: what is live, and ending any of them.
 * <p>
 * Everything here is scoped to the caller's own account. A session id that
 * belongs to somebody else is answered exactly as one that does not exist -
 * 404 - so this cannot be used to find out whether an id is real.
 */
@RestController
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    /** Signing out for real: the token stops working here, not just on this phone. */
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout() {
        CurrentAccount caller = requireCaller();
        sessions.revokeOwn(caller.accountId(), caller.sessionId(), SessionRevocation.SIGNED_OUT);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/auth/sessions")
    public ResponseEntity<List<AccountSession>> mySessions() {
        CurrentAccount caller = requireCaller();
        return ResponseEntity.ok(sessions.liveSessions(caller.accountId(), caller.sessionId()));
    }

    /**
     * Ends one of her devices. Ending the one she is holding is simply
     * signing out, which is allowed - the app treats the next 401 as such.
     */
    @DeleteMapping("/api/v1/auth/sessions/{sessionId}")
    public ResponseEntity<Void> endSession(@PathVariable UUID sessionId) {
        CurrentAccount caller = requireCaller();
        if (!sessions.revokeOwn(caller.accountId(), sessionId, SessionRevocation.SIGNED_OUT)) {
            throw ApiException.notFound("No such session");
        }
        return ResponseEntity.noContent().build();
    }

    private CurrentAccount requireCaller() {
        return CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }
}
