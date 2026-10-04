-- A sample shop: an example in the SheOut Seller directory, shown while
-- MARKETPLACE_SAMPLE_SHOPS is on and removed when it is turned off. The app
-- marks it "Sample shop" and offers no way to contact it - there is nobody
-- behind it. See the marketplace's SampleShops.
alter table seller_profiles add column sample boolean not null default false;
