package com.sheout.notifications.internal.channel;

import com.sheout.notifications.internal.NotificationError;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public Result<Void, NotificationError> send(String recipient, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", recipient);
        form.add("From", fromNumber);
        form.add("Body", message);
        try {
            restClient.post()
                    .uri(MESSAGES_URL, accountSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return Result.success(null);
        } catch (RestClientException e) {
            log.error("Twilio SMS send failed (to a redacted recipient)", e);
            return Result.failure(NotificationError.PROVIDER_ERROR);
        }
    }
}
