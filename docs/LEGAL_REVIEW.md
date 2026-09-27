# Copy awaiting legal review

To be reviewed together with the Terms of Service. Each item is live in the
apps as written below; change the string, not the screen, when the review
comes back.

## SheOut Seller (marketplace)

SheOut lists sellers for a flat one-time listing fee and is not a party to
any sale. The apps say so in two places:

| Where | String key (rider app) | English |
|---|---|---|
| Seller application, and the foot of the seller's own shop screen | `seller.legal.applicationNotice` | SheOut is a discovery platform - you handle your own sales, payments, and delivery directly with customers. SheOut charges a flat listing fee only and is not party to your transactions. |
| Every product page shown to customers | `seller.legal.productNotice` | This sale happens directly with the seller. SheOut is not involved in payment or delivery for this listing. |

Related wording in the same flows, worth reading alongside:

- `seller.apply.understood` - the checkbox a seller must tick before applying:
  "I understand that I handle my own sales, payments and delivery, and that
  SheOut is not part of them."
- `seller.directory.notInvolved` - under the directory list.
- `seller.product.priceNote` - "Price set by the seller. Confirm it with her before you buy."
- `seller.fee.oneTime` - "One-time fee. SheOut takes nothing from your sales."

The Hindi and Telugu versions (`hi.json`, `te.json`) are machine-quality
translations of the above and need the same review; see
[SAFETY_TRANSLATION_REVIEW.md](SAFETY_TRANSLATION_REVIEW.md) for how the
other translated safety copy is being handled.

Open questions for the review:

- Whether the Terms of Service need a SheOut Seller section (listing fee,
  refund policy for the fee if a shop is suspended, content rules, and
  the platform's non-involvement in sales). None exists yet.
- Whether showing a seller's phone/WhatsApp number to every signed-in rider
  needs explicit consent wording in the application beyond the form
  labels.
