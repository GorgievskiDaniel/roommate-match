-- "Наместен" is no longer offered as an amenity.
DELETE FROM listing_amenities WHERE amenity = 'FURNISHED';
ALTER TABLE listing_amenities DROP CONSTRAINT listing_amenities_amenity_check;
ALTER TABLE listing_amenities ADD CONSTRAINT listing_amenities_amenity_check CHECK (amenity IN ('PARKING', 'BALCONY',
    'YARD', 'WIFI', 'AIR_CONDITIONING', 'CENTRAL_HEATING', 'WASHING_MACHINE', 'DISHWASHER', 'ELEVATOR'));
