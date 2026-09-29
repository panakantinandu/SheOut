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

## SheOut Assistant (help chat): Privacy Policy must name Anthropic

**Needs a change before the assistant is switched on for real users.** The
Privacy Policy's third-party list (`frontend/design-system/src/legal/content.ts`,
the paragraph beginning "These are the only third parties involved") says the
list is complete. It does not mention that assistant messages are sent to
Anthropic, so the list is wrong for anyone who uses the chat.

Proposed addition to that list, for legal review:

> Anthropic, only if you use the SheOut Assistant help chat. Receives the
> messages you type in that chat (the last few of the conversation) and our
> help content, and sends back an answer. It does not receive your name, phone
> number or account details from us. SheOut does not keep the conversation
> unless you choose to turn it into a support ticket, in which case the
> ticket (with the conversation) is kept like any other ticket.

Facts behind the wording, for the reviewer:

- Messages go from SheOut's server to Anthropic's API; the key is never in the
  apps. Only the last 6 messages are sent.
- The chat asks people not to share OTPs, passwords or ID numbers (under the
  message box). A message that sounds like an emergency is **not** sent to
  Anthropic: the app shows SOS and 112 instead.
- SheOut stores no transcript: only per-account daily counts (messages,
  tokens, hand-offs, emergencies) for the usage caps and the cost view.
- A transcript is stored only when she sends a support ticket from the chat,
  pre-filled with the conversation, which she can edit before sending.
- Anthropic's own retention of API inputs is set by SheOut's commercial terms
  with Anthropic; the reviewer should confirm those before finalising the
  wording.

When the wording is approved, change the string and bump `LEGAL_VERSION`, so
everyone is asked to accept the new version (see `content.ts`).

## SheOut Marketplace: discounts, and search "in your own words"

**1. Honest "was" prices.** A seller may now give a product an original price,
shown struck through above her price with the percentage off worked out by the
app ("₹6,000 ₹4,500 25% off"). The app only checks that the original price is
above the price; whether it was ever really charged is her declaration. She
sees this line where she types it (product editor) and in the application
flow (Seller Registration, Step 3), key `seller.legal.genuinePrice`:

> An original price must be a price you genuinely charged before, not a higher
> figure made up to make a discount look bigger. Indian consumer law requires
> price and discount claims to be honest.

For the reviewer: the brief cited "India's Legal Metrology (Consumer) rules".
The user-facing line deliberately names no instrument until the right one is
confirmed - likely candidates are the Consumer Protection Act, 2019 and the
CCPA's Guidelines for Prevention of Misleading Advertisements (2022), and the
Legal Metrology (Packaged Commodities) Rules, 2011 for MRP. Please confirm the
citation, whether the seller terms need a matching clause, and whether SheOut
should be able to remove a discount it believes is not genuine (the console
shows every original price to operators).

**2. Privacy Policy: marketplace searches reach Anthropic.** "Search in your
own words" sends the words typed and up to 60 current public listings (title,
category, price, area, shop name, first 160 characters of the description) to
Anthropic's API, and gets back which listings fit. No name, phone number or
account detail is sent; nothing typed is stored, only a per-account daily
count and token totals for the usage cap. The Anthropic entry above should
say it covers this search as well as the help chat.
