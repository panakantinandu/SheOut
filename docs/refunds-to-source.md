# Refunds to the original payment method

For finance and support. The Refund and Cancellation Policy
(`/refunds`) promises customers this, so follow it exactly.

## Where refunds go

| Case | Where the money goes | Who does it | Deadline |
|---|---|---|---|
| Anything else: a long route, a bad trip, a goodwill credit, a refund she asks for | Her SheOut wallet, at once | Support or manager, from the console's **Refund** button (owner approval above their limit) | Immediately |
| A payment **failed** but money left her account | Back to the card, UPI or bank account she paid with | Finance, from the Razorpay dashboard | `REFUND_TO_SOURCE_WORKING_DAYS` (default **7 working days**) from approval |
| She was **charged twice** for the same trip or top-up | Same: back to how she paid | Finance | Same |
| **SheOut cancelled** a trip she had already paid for online | Same: back to how she paid | Finance | Same |

The number of days is in two places, which CI keeps equal
(`scripts/check-legal-sync.mjs`):

- the server: `sheout.refunds.to-source-working-days` / `REFUND_TO_SOURCE_WORKING_DAYS`;
- the policy page: `refundToSourceWorkingDays` in
  `frontend/design-system/src/legal/legal-entity.config.json`.

Change both together. A longer period is a change to a promise already made,
so it needs a new policy version first.

## Steps (finance)

SheOut has no Razorpay refund API yet, so this is done by hand.

1. Find the payment in the Razorpay dashboard (Transactions, Payments) by the
   order or payment ID shown on the trip in the console.
2. Check the case really is one of the three above. A failed payment that
   Razorpay already auto-refunded needs nothing; tell support.
3. Issue a full refund from Razorpay (Payments, the payment, Refund). Use the
   normal speed unless the customer is in hardship.
4. Write the Razorpay refund ID, the amount and the date on the support
   ticket, and close it with a message to the customer that says when to
   expect the money.
5. **Do not also refund to her wallet** for the same money.

## When this changes

When a Razorpay refund API is built into the payments module, these three
cases should be handled from the console, with the refund ID stored against
the trip. Update this page and the policy at the same time.
