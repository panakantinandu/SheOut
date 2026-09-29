-- Where a SheOut Seller works from, in her own words: a locality or the
-- areas she serves ("Kukatpally", "Madhapur and Gachibowli"). Free text, not
-- coordinates - the sale and any delivery happen off the platform, so a
-- customer only needs to know whether she is nearby, and the directory
-- filters on it as text. Optional, so every existing shop stays valid.
alter table seller_profiles
    add column area varchar(80);
