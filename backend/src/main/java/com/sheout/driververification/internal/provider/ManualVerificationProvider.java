package com.sheout.driververification.internal.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The default: no vendor. Operators obtain and read police certificates and
 * any background report themselves, and record them in the console. This
 * sends nothing, fetches nothing, and keeps the webhook closed.
 * <p>
 * A real vendor is a new implementation selected by
 * VERIFICATION_PROVIDER=&lt;name&gt; - see VerificationProvider.
 */
@Component
@ConditionalOnProperty(name = "sheout.verification.provider", havingValue = "manual", matchIfMissing = true)
public class ManualVerificationProvider implements VerificationProvider {

    @Override
    public String name() {
        return "manual";
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public Submission submitBackgroundCheck(Request request) {
        throw new IllegalStateException("No verification provider is configured; operators record checks by hand");
    }

    @Override
    public Optional<Result> fetchResult(String providerReference) {
        return Optional.empty();
    }

    @Override
    public List<Result> parseWebhook(byte[] body) {
        return List.of();
    }
}
