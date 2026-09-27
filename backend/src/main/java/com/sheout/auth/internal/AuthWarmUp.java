package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.internal.security.JwtService;
import com.sheout.sharedkernel.warmup.WarmUp;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * What every signed-in request pays first: signing and checking a token,
 * and looking up its session. The token is for a made-up account and a
 * session that does not exist, so it is refused as signed out wherever it
 * is sent - and it is only ever sent to this instance itself. See
 * sharedkernel.warmup.WarmUp.
 */
@Component
class AuthWarmUp implements WarmUp {

    private final JwtService jwt;
    private final SessionService sessions;

    AuthWarmUp(JwtService jwt, SessionService sessions) {
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public void inProcess() {
        UUID session = UUID.randomUUID();
        jwt.parse(jwt.issue(UUID.randomUUID(), AccountRole.CUSTOMER, session));
        sessions.checkLive(session);
    }

    @Override
    public List<Request> requests() {
        String token = jwt.issue(UUID.randomUUID(), AccountRole.CUSTOMER, UUID.randomUUID());
        return List.of(
                new Request("GET", "/api/v1/bookings/me", null, token),
                new Request("GET", "/api/v1/users/customer/me", null, token));
    }
}
