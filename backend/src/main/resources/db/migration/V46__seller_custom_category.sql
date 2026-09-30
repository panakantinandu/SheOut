-- A seller may choose "Other" and say what she sells in her own words
-- ("Homemade pickles"); those words are shown to buyers in place of the
-- category name and are matched by the directory's search. Null for the
-- six named categories.
alter table seller_profiles add column custom_category varchar(40);
