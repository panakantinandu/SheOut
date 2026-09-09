package com.sheout.payments.internal.gateway;

/** The one thing PaymentService needs back from creating an order: an id to store and to match a later webhook against. */
public record GatewayOrder(String orderId) {
}
