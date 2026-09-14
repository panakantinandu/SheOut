/**
 * Payouts module - what SheOut owes its partners and how it reaches them.
 * <p>
 * Its own module rather than more of payments or users. payments collects
 * from riders through a gateway; this pays partners out, by hand, on a
 * different lifecycle with a different operator workflow. users owns who a
 * partner is; bank and UPI details are financial data with a narrower
 * audience, and keeping them here keeps them out of every profile read.
 * <p>
 * The wallet is credited by reacting to payments' PaymentCaptured event,
 * never by polling payments and never by payments calling in. The listener
 * runs inside the capture's transaction, so a capture and its credit commit
 * together.
 * <p>
 * Deliberately manual: an operator sends the money and records the
 * reference. No payout API is integrated.
 */
package com.sheout.payouts;
