package com.sheout.notifications.internal.channel;

import com.sheout.notifications.internal.NotificationError;
import com.sheout.notifications.internal.SendFailure;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * account-sid/auth-token/from-number are blank-by-default (see
 * application.yml) - same "not wired up in every environment yet"
 * treatment RazorpayPaymentGateway gives Razorpay's credentials. There is
 * deliberately no isConfigured() short-circuit before attempting a send:
 * RazorpayPaymentGateway doesn't gate either, and for the same reason - a
 * real call with blank/invalid credentials still reaches Twilio's servers
 * and fails there (a real 401, not a locally-fabricated one), which is
 * what a caller (and this module's own tests) should be able to rely on
 * when checking "did this actually try, or fail before it even asked."
 */
@Component
public class TwilioSmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsChannel.class);
    private static final String MESSAGES_URL = "https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json";
    private static final int TIMEOUT_MS = 8000;

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final RestClient restClient;

    public TwilioSmsChannel(
            @Value("${sheout.notifications.sms.twilio.account-sid:}") String accountSid,
            @Value("${sheout.notifications.sms.twilio.auth-token:}") String authToken,
            @Value("${sheout.notifications.sms.twilio.from-number:}") String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        // Not SimpleClientHttpRequestFactory: it is backed by
        // HttpURLConnection, whose built-in Basic-auth handling consumes the
        // body of a 401 before anything here can read it. A 401 is precisely
        // the response that matters most here - Twilio names the cause in its
        // body - and it arrived empty, which is a large part of why this
        // outage stayed opaque. The JDK HttpClient hands the error body back
        // untouched.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(TIMEOUT_MS));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Result<Void, SendFailure> send(String recipient, String message) {
        // Credentials missing entirely is worth saying plainly rather than
        // letting it surface as an opaque 401 from Twilio.
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            String missing = (accountSid.isBlank() ? "account-sid " : "")
                    + (authToken.isBlank() ? "auth-token " : "")
                    + (fromNumber.isBlank() ? "from-number" : "");
            log.error("Twilio SMS not attempted - unset config: {}", missing.trim());
            return Result.failure(SendFailure.of(NotificationError.PROVIDER_ERROR,
                    "Twilio is not configured on this deployment (missing: " + missing.trim() + ")"));
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", recipient);
        form.add("From", fromNumber);
        form.add("Body", message);

        log.debug("Twilio SMS send attempt - To: [{}], From: [{}], AccountSid: [{}]", recipient, fromNumber, accountSid);

        try {
            // exchange(), not retrieve().toBodilessEntity(): the bodiless form
            // discards the error response, leaving only a status code. Twilio
            // puts the actionable part - its own error code and a sentence
            // saying what is wrong - in the body and the X-Twilio-Error-Code
            // header, so both are read here.
            return restClient.post()
                    .uri(MESSAGES_URL, accountSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return Result.success(null);
                        }
                        String body = "";
                        try (InputStream in = response.getBody()) {
                            body = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
                        } catch (IOException ignored) {
                            // Body unreadable - the status and error code below still say plenty.
                        }
                        String twilioCode = response.getHeaders().getFirst("X-Twilio-Error-Code");
                        String detail = "HTTP " + response.getStatusCode().value()
                                + (twilioCode == null ? "" : " (Twilio error " + twilioCode + ")")
                                + (body.isEmpty() ? "" : " " + body);
                        log.error("Twilio SMS send failed - To: [{}], From: [{}], {}", recipient, fromNumber, detail);
                        return Result.failure(SendFailure.of(NotificationError.PROVIDER_ERROR, detail));
                    });
        } catch (RestClientException e) {
            // Connect/read timeout, DNS, TLS - never reached Twilio at all.
            String detail = "Could not reach Twilio: " + e.getMessage();
            log.error("Twilio SMS send failed - To: [{}], From: [{}], {}", recipient, fromNumber, detail);
            return Result.failure(SendFailure.of(NotificationError.PROVIDER_ERROR, detail));
        }
    }
}
