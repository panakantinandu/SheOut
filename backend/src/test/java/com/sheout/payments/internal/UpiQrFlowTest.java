package com.sheout.payments.internal;

import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.UpiQr;
import com.sheout.payments.internal.gateway.GatewayQr;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.payments.internal.web.RazorpayWebhookController;
import com.sheout.sharedkernel.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The partner's UPI QR against the real database: one live QR per fare,
 * captured by webhook or by the partner's screen asking, and a second
 * payment for an already-paid fare refunded, never kept. Razorpay itself is
 * stood in for.
 * <p>
 * Like SheOutApplicationTests, this needs the local Postgres and Redis.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@RecordApplicationEvents
@DirtiesContext
class UpiQrFlowTest {

    @Autowired PaymentService payments;
    @Autowired PaymentRepository repository;
    @Autowired RazorpayWebhookController webhook;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents events;
    @MockBean PaymentGateway gateway;

    private final UUID booking = UUID.randomUUID();
    private final AtomicInteger qrs = new AtomicInteger();

    @BeforeEach
    void setUp() {
        PaymentEntity row = new PaymentEntity(booking, new BigDecimal("172.14"), PaymentMethod.UPI, PaymentStatus.PENDING);
        row.setFareAmount(new BigDecimal("272.14"));
        repository.save(row);
        when(gateway.createUpiQr(any(), any(), anyString(), any())).thenAnswer(inv ->
                Result.success(new GatewayQr("qr_test_" + booking + "_" + qrs.incrementAndGet(),
                        "https://rzp.io/qr/" + qrs.get() + ".png", inv.getArgument(3))));
        when(gateway.refund(anyString())).thenReturn(Result.success(null));
        when(gateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from payments where booking_id = ?", booking);
    }

    @Test
    void oneQrPerFareUntilItIsNearlyClosed() {
        UpiQr first = ok(payments.upiQrForTrip(booking));
        assertThat(first.amount()).isEqualByComparingTo("172.14");
        assertThat(first.expiresAt()).isAfter(Instant.now().plusSeconds(10 * 60));
        assertThat(ok(payments.upiQrForTrip(booking)).imageUrl()).isEqualTo(first.imageUrl());

        // Nearly closed: a fresh one, and the old one closed.
        jdbc.update("update payments set qr_expires_at = now() + interval '1 minute' where booking_id = ?", booking);
        UpiQr second = ok(payments.upiQrForTrip(booking));
        assertThat(second.imageUrl()).isNotEqualTo(first.imageUrl());
        verify(gateway).closeQr("qr_test_" + booking + "_1");
    }

    @Test
    void theWebhookCapturesTheFareAndASecondPaymentIsRefunded() {
        ok(payments.upiQrForTrip(booking));
        String qrId = repository.findByBookingId(booking).orElseThrow().getRazorpayQrId();
        events.clear();

        webhook.handle(qrCredited(qrId, "pay_qr_1", 17214), "sig");

        PaymentEntity paid = repository.findByBookingId(booking).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(paid.getMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(paid.getRazorpayPaymentId()).isEqualTo("pay_qr_1");
        assertThat(paid.getDriverPayout()).as("the partner's share is settled as for any fare").isNotNull();
        assertThat(events.stream(PaymentCaptured.class)).hasSize(1);

        // The same delivery again changes nothing and refunds nothing.
        webhook.handle(qrCredited(qrId, "pay_qr_1", 17214), "sig");
        verify(gateway, never()).refund(anyString());

        // A second, different payment for a fare already paid is given back.
        webhook.handle(qrCredited(qrId, "pay_qr_2", 17214), "sig");
        verify(gateway).refund("pay_qr_2");
        assertThat(events.stream(PaymentCaptured.class)).hasSize(1);
    }

    @Test
    void thePartnersScreenFindsThePaymentWithoutTheWebhook() {
        ok(payments.upiQrForTrip(booking));
        String qrId = repository.findByBookingId(booking).orElseThrow().getRazorpayQrId();
        when(gateway.qrPayments(qrId)).thenReturn(Result.success(List.of()));
        assertThat(ok(payments.checkUpiQr(booking)).status()).isEqualTo(PaymentStatus.PENDING);

        when(gateway.qrPayments(qrId)).thenReturn(Result.success(List.of(
                new GatewayQr.QrPayment("pay_qr_9", 17214, "captured", PaymentMethod.UPI))));
        assertThat(ok(payments.checkUpiQr(booking)).status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payments.upiQrForTrip(booking).error()).as("no QR for a paid fare").isNotNull();
    }

    @Test
    void theWrongAmountIsNeverTakenAsTheFare() {
        ok(payments.upiQrForTrip(booking));
        String qrId = repository.findByBookingId(booking).orElseThrow().getRazorpayQrId();

        webhook.handle(qrCredited(qrId, "pay_qr_short", 100), "sig");

        assertThat(repository.findByBookingId(booking).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(gateway).refund("pay_qr_short");
    }

    private static String qrCredited(String qrId, String paymentId, long paise) {
        return """
                {"event":"qr_code.credited","payload":{
                  "payment":{"entity":{"id":"%s","amount":%d,"method":"upi","status":"captured","order_id":null}},
                  "qr_code":{"entity":{"id":"%s","status":"closed"}}}}
                """.formatted(paymentId, paise, qrId);
    }

    private static <T, E> T ok(Result<T, E> result) {
        assertThat(result.isSuccess()).as(() -> "expected success, got " + (result.isFailure() ? result.error() : "")).isTrue();
        return result.value();
    }
}
