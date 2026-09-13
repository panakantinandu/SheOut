package com.sheout.support.internal.web;

import com.sheout.support.SupportApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where both apps get the support number for their Help screens.
 * <p>
 * Unauthenticated on purpose. Somebody who cannot sign in is exactly the
 * person most likely to need to reach a human, and making the way to reach
 * one depend on a working session would fail precisely when it is needed.
 * Nothing is disclosed by it either: this is a published business number,
 * not anybody's personal one.
 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final SupportApi supportApi;

    SupportController(SupportApi supportApi) {
        this.supportApi = supportApi;
    }

    @GetMapping("/contact")
    public ResponseEntity<SupportContactResponse> contact() {
        // Null rather than an empty string when unset, so a client reading
        // this cannot accidentally render "tel:" as a working link.
        return ResponseEntity.ok(new SupportContactResponse(supportApi.supportPhoneNumber().orElse(null)));
    }

    public record SupportContactResponse(String phoneNumber) {
    }
}
