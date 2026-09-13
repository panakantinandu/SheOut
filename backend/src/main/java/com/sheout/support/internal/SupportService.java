package com.sheout.support.internal;

import com.sheout.support.SupportApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SupportService implements SupportApi {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final String phoneNumber;

    SupportService(@Value("${sheout.support.phone-number:}") String phoneNumber) {
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();
        if (this.phoneNumber.isBlank()) {
            // A warning, not a startup failure. Chat covers everything
            // routine, and SOS does not depend on this at all, so an
            // unconfigured support line degrades the product rather than
            // breaking it - but somebody should know it is missing.
            log.warn("SUPPORT_PHONE_NUMBER is not set - both apps will hide their Contact Support action");
        }
    }

    @Override
    public Optional<String> supportPhoneNumber() {
        return phoneNumber.isBlank() ? Optional.empty() : Optional.of(phoneNumber);
    }
}
