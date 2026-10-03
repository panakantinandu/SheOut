# Pending decisions

SheOut is pre-launch: the company is still being registered, so there is no
GSTIN, no insurer and no lawyer's opinion yet. Every decision below is
**pending: company being registered**. Each has a safe default in the code
today, and the table says who must answer it and exactly what changes when
they do.

**Who answers:** *Broker* - the insurance broker (and through them the
insurer). *Lawyer* - SheOut's lawyer. *CA* - SheOut's chartered accountant.
*Founder* - a business decision.

**Where settings live:** environment variables are set on Render in the
`sheout-app-config` group of `render.yaml` (shared by production and
staging) or in the dashboard for secrets, and listed in `.env.example`.
Policies are entered in the console, never in configuration.

## Insurance

| # | Decision | Who | Safe default today | When answered, change |
|---|---|---|---|---|
| 1 | Insurer, master policy number, sum insured and premium per trip for **passenger** cover (MVAG 2025 expects at least ₹5 lakh per trip) | Broker | No policy. No trip is covered, no app shows "Insured trip", the console shows "Trips are NOT insured". Rides are **not** refused: `INSURANCE_REQUIRED_FOR_RIDES=false` | Enter the policy in the console (Insurance page) and switch it on; **then** set `INSURANCE_REQUIRED_FOR_RIDES=true`. Never the other way round - with it true and no policy, every ride is refused |
| 2 | Does cover attach when the trip starts, or only once the trip is declared (in the daily bordereau)? | Broker | Attaches at trip start: the chip shows on a live trip with cover, and hides if reporting failed | If at declaration: `INSURANCE_BADGE_REQUIRES_REPORTED=true` |
| 3 | Goods-in-transit cover for parcels | Broker | None; parcels are not covered and nothing says they are | Console: a `GOODS_IN_TRANSIT` policy |
| 4 | Partner group covers (health, term life, accident): insurer, policy, premium per member per year | Broker / Founder | None; no partner is put forward, no "Your cover" card | Console: a `PARTNER_*` policy. Partners verified *after* it is switched on are put forward automatically; earlier ones need enrolling (not built) |
| 5 | How covered trips are declared (file layout, or an API) | Broker | A daily CSV an operator downloads and sends (`INSURANCE_REPORTER=csv`) | Layout: change `CsvBordereauReporter`. API: implement `ApiInsurerReporter` (a stub today) and set `INSURANCE_REPORTER=api` |

## Partner verification

| # | Decision | Who | Safe default today | When answered, change |
|---|---|---|---|---|
| 6 | How often a police check must be redone | Lawyer | Every 12 months | `POLICE_REVERIFY_MONTHS` |
| 7 | Is a private background check alone enough as "police verification" in Telangana? | Lawyer | **No**: it can sit beside a police certificate, never replace it | `POLICE_ACCEPT_THIRD_PARTY_BGV_ALONE=true` only if the lawyer says yes |
| 8 | Do autos and cabs need a fitness certificate checked at onboarding? | Lawyer | Yes, required for autos and cabs (bikes and parcels: no) | `PARTNER_DOCS_AUTO`, `PARTNER_DOCS_CAB` (remove `FITNESS_CERTIFICATE`) |
| 9 | Which documents each service needs at all | Lawyer | Rides and parcels: licence, RC, insurance, PUC; insurance must be commercial use for rides | `PARTNER_DOCS_<SERVICE>`, `COMMERCIAL_INSURANCE_SERVICES` |
| 10 | The wording of the partner's verification consent, and of sharing her data with insurers | Lawyer | A draft, every paragraph marked `[to be reviewed by lawyer]`, version `2026-10-03-draft` | Change `PARTNER_VERIFICATION_CONSENT` in `frontend/design-system/src/legal/content.ts` **and** bump `VERIFICATION_CONSENT_VERSION` there and in configuration, to the same value. Every partner is then asked to agree again |
| 11 | Aadhaar: keep the ID photo (the app invites her to cover the number), or require masked Aadhaar / DigiLocker | Lawyer | Unchanged: any government ID photo, number maskable | Code change in the ID step (not built) |
| 12 | A background-verification vendor (AuthBridge, IDfy, OnGrid or similar) | Founder | None: operators record checks by hand (`VERIFICATION_PROVIDER=manual`, webhook closed) | Implement `VerificationProvider` for that vendor from its real documentation, then set `VERIFICATION_PROVIDER`, `VERIFICATION_PROVIDER_WEBHOOK_SECRET`, `VERIFICATION_PROVIDER_SIGNATURE_HEADER`, and name the vendor in the privacy policy |

## Tax

| # | Decision | Who | Safe default today | When answered, change |
|---|---|---|---|---|
| 13 | GST registration (GSTIN) and the registered legal name | CA | GST off: no tax split, no invoice (`GST_ENABLED=false`) | `SHEOUT_GSTIN` (dashboard), `SHEOUT_LEGAL_NAME` |
| 14 | GST rate and SAC code for bike taxi, auto, cab, parcel, SheOut's platform fee and the seller listing fee | CA | All empty - nothing assumed | `GST_RATE_*`, `GST_SAC_*` (all twelve). Then `GST_ENABLED=true`; the server refuses to start if any is missing |
| 15 | Is SheOut the supplier of app-booked rides under Section 9(5), or only of its platform service? | CA | Invoices are issued by SheOut, in SheOut's GSTIN, for the whole fare (the 9(5) reading) | If only the platform fee: code change in `PaymentService.applyGst` (invoice the commission instead) |
| 16 | Is tax worked out after promotions? Who bears it - inside SheOut's commission (today) or out of the fare before the partner's share? | CA / Founder | On what the rider paid; a trip a promotion paid in full gets no invoice; the partner's share is unchanged, so SheOut bears the tax | Code change in `PaymentService` / `GstService` |
| 17 | Invoice format and numbering; e-invoicing (IRN) | CA | A plain one-page PDF, numbered `SO/2026-27/000001` | `GST_INVOICE_PREFIX`; a new `InvoiceRenderer` |
| 18 | Commission model vs subscription | Founder / CA / Lawyer | Commission, 18% (`PLATFORM_COMMISSION_PERCENT`), never above 20% (`PLATFORM_COMMISSION_MAX_PERCENT`, the MVAG 80% rule) | A subscription changes 15-16 and the 80% rule's application - revisit all three |

## Company details already waiting (see `docs/LEGAL_REVIEW.md`)

| # | Decision | Who | Safe default today | When answered, change |
|---|---|---|---|---|
| 19 | Registered entity, address, grievance officer, data-retention periods in the privacy policy and terms | Founder / Lawyer | Visible `[to be completed by SheOut]` placeholders | `frontend/design-system/src/legal/content.ts`; bump `LEGAL_VERSION`; `GRIEVANCE_OFFICER_EMAIL` |
| 20 | Copyright holder in `LICENSE` and `NOTICE` | Founder | The individual, until the company exists | Both files |
| 21 | Privacy policy naming Anthropic (help assistant, marketplace search) | Lawyer | Not yet named; see `docs/LEGAL_REVIEW.md` | `content.ts`; bump `LEGAL_VERSION` |

## Staff console (see `docs/ADMIN_SECURITY.md`)

| # | Decision | Who | Safe default today | When answered, change |
|---|---|---|---|---|
| 22 | Who the owners are | Founder | **Decided 2026-10-03:** two owners - the founder now (set `SHEOUT_OWNER_BOOTSTRAP_EMAIL` in the Render dashboard), a second invited later from Staff | - |
| 23 | Refund limits | Founder | **Decided:** support ₹200, manager ₹1,000, above that an owner approves (`REFUND_LIMIT_SUPPORT`, `REFUND_LIMIT_MANAGER`). Finance not decided: ₹200 (`REFUND_LIMIT_FINANCE`) | Finance's figure |
| 24 | IP allowlist for owner and finance | Founder | **Decided:** off for now; built, so `STAFF_IP_ALLOWLIST_<ROLE>` turns it on | Set the networks when wanted |
| 25 | Session lengths | Founder | **Decided:** 30 min idle / 8 h for most; safety responders 60 min / 12 h; owners 15 min / 8 h (all configurable) | - |
| 26 | Auditor account for the CA | Founder / CA | **Decided:** supported, read-only finance, access 30 days unless another date is given; none created yet | Invite as Auditor from Staff when needed |
