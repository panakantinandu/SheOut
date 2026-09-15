-- Profile completeness for both roles: a date of birth (the Terms say nobody
-- under 18 may use SheOut, and until now nothing checked), an optional
-- contact email (payment receipts, and support replies when push is not
-- set up), and for riders the profile photo partners already have.
--
-- All nullable: existing accounts have none of these yet. The apps send an
-- incomplete profile to the completion screen, and saving a profile refuses
-- one without a date of birth or a photo - see CustomerProfileService and
-- DriverProfileService.

alter table customer_profiles add column date_of_birth date;
alter table customer_profiles add column email varchar(254);
alter table customer_profiles add column profile_photo_key varchar(500);

alter table driver_profiles add column date_of_birth date;
alter table driver_profiles add column email varchar(254);
