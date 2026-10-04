-- Where a parcel for a seller's order is collected - a point she chooses on
-- the map (home, shop, or a gate she meets the partner at). Null until she
-- sets one. Only handed to a buyer who books a parcel for one of her
-- products, never with the product page every visitor sees: the area she
-- typed stays the only location on her public listing.
alter table seller_profiles
    add column parcel_pickup_label varchar(200),
    add column parcel_pickup_lat   double precision,
    add column parcel_pickup_lng   double precision;
