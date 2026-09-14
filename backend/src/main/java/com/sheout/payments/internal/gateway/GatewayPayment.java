package com.sheout.payments.internal.gateway;

import com.sheout.payments.PaymentMethod;

/** A payment Razorpay has confirmed as captured, and how the rider paid. */
public record GatewayPayment(String paymentId, PaymentMethod method) {
}
