/**
 * Payments module - fare charging, payment method storage, refunds, and
 * driver payout accounting. Talks to the payment gateway (Razorpay).
 * <p>
 * Only classes declared directly in this package are this module's public
 * API. Everything under {@code com.sheout.payments.internal} is private to
 * this module - other modules must not import from it.
 */
package com.sheout.payments;
