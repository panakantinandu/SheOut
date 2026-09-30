-- What a buyer needs beyond a name and a price: whether she can have it
-- (and how many, or how soon), what the price is per, any minimum, the
-- sizes or colours it comes in, how it reaches her, and whether it can go
-- back. Every existing product reads as in stock, priced per piece, which is
-- what it was presented as until now.
alter table seller_products add column availability       varchar(20)  not null default 'IN_STOCK';
alter table seller_products add column quantity_available integer;
alter table seller_products add column ready_in_days      integer;
alter table seller_products add column price_unit         varchar(20)  not null default 'PIECE';
alter table seller_products add column min_order_quantity integer;
alter table seller_products add column options            varchar(200);
alter table seller_products add column fulfilment         varchar(100);
alter table seller_products add column delivery_note      varchar(300);
alter table seller_products add column return_policy      varchar(20);
