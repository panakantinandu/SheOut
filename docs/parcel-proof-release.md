# Parcel proof of delivery: release notes

Branch `feat/parcel-proof-and-dispatch`. Not deployed. The decisions it
implements are in `docs/parcel-dispatch-audit.md` (Part 1); queued offers
(Part 2) are designed in `docs/queued-dispatch-design.md` and not built.

## Migrations

| Version | What | Risk |
|---|---|---|
| **V80** `parcel_proof_of_delivery` | New tables `parcel_deliveries`, `parcel_photos`, `earning_holds`; adds `driver_wallets.held` (default 0) | Additive only. Nothing existing changes. |
| **V81** `one_active_trip_per_partner` | Partial unique index: one booking per `driver_id` in MATCHED, ACCEPTED or IN_PROGRESS | Fails the deploy, and changes nothing, if any partner already holds two active trips. Run the check below first. |

V78 (on `next/drop-rc-document-key-v78`) and V79 (the proposed customer-wording
migration in `docs/ux-audit.md`) are reserved, so these take V80 and V81.
Flyway applies them in version order whenever V78 and V79 ship; neither
depends on the other.

### Read-only check before deploying V81 (production)

```sql
-- Read-only. Expect zero rows. Any row is a partner holding two live trips:
-- settle those trips in the console, then deploy.
select driver_id, count(*) as active_trips, array_agg(id) as bookings, array_agg(status) as statuses
  from bookings
 where driver_id is not null
   and status in ('MATCHED', 'ACCEPTED', 'IN_PROGRESS')
 group by driver_id
having count(*) > 1;
```

Checked locally on `sheout`, `sheout_e2e`, `sheout_qa` and `sheout_release`:
zero rows on each. Production has not been checked (not touched from here).

## New settings (all default to the safe value)

| Env var | Default | What |
|---|---|---|
| `PARCEL_DELIVERY_RADIUS_M` | 150 | "Delivered" only this close to the drop; outside it a reason is required and the delivery is flagged for review |
| `PARCEL_MAX_WEIGHT_KG` | 5 | Shown to the sender, who confirms the parcel is under it and fits a bag |
| `PARCEL_MAX_DECLARED_VALUE` | 5000 | Optional declared value cap, records only |
| `PARCEL_PROHIBITED_ITEMS_VERSION` | 2026-10-11 | Change it whenever the list changes |
| `PARCEL_PHOTOS_REQUIRED` | true | Pickup, delivery and receiver-absent photos required. Turn off only if uploads are broken; the code is still required |
| `PARCEL_PHOTO_RETENTION_DAYS` | 90 | Photos and receivers' numbers are deleted after this, unless a dispute is open |
| `PARCEL_DISPUTE_WINDOW_HOURS` | 24 | A parcel's earnings are held this long after delivery |
| `PARCEL_RECEIVER_SMS_ENABLED` | false | Texts the delivery code to the receiver. Leave off until DLT approves the template in `ParcelReceiverSms` |

No render.yaml change is needed: every default is the intended production value.

## In-flight deliveries at deploy

A delivery booked before the deploy has no `parcel_deliveries` row and
finishes under the old rules (no code, no photos). Only new bookings need
the receiver's details, so nothing under way is stranded.
