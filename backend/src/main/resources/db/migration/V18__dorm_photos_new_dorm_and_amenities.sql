-- The folder of the dorm's photos, and when a dorm still being built opens.
ALTER TABLE dorms ADD COLUMN slug VARCHAR(40) UNIQUE;
ALTER TABLE dorms ADD COLUMN opens_on DATE;

UPDATE dorms SET slug = 'goce-delchev' WHERE name = 'ДСД Гоце Делчев';
UPDATE dorms SET slug = 'stiv-naumov' WHERE name = 'Стив Наумов';
UPDATE dorms SET slug = 'koco-racin' WHERE name = 'ДСД Кочо Рацин';
UPDATE dorms SET slug = 'nikola-karev' WHERE name = 'ДСД Никола Карев';
UPDATE dorms SET slug = 'braka-miladinovci' WHERE name = 'ДСД Браќа Миладиновци';
UPDATE dorms SET slug = 'tetovo' WHERE name = 'ДСД Тетово';
UPDATE dorms SET slug = 'hiljadnikov' WHERE name = 'ПСД Хилјадников';
UPDATE dorms SET slug = 'logos' WHERE name = 'ПСД Логос';

-- Бул. „Митрополит Теодосиј Гологанов“ 26б, at the corner with бул. „Св. Климент Охридски“.
INSERT INTO dorms (name, latitude, longitude, slug, opens_on)
VALUES ('ДСД Кузман Јосифовски', 41.99573, 21.42401, 'kuzman-josifovski', DATE '2027-01-01');

-- What a flat or house offers. This replaces the single parking flag.
CREATE TABLE listing_amenities (
    listing_id BIGINT      NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    amenity    VARCHAR(30) NOT NULL CHECK (amenity IN ('PARKING', 'BALCONY', 'YARD', 'WIFI', 'AIR_CONDITIONING',
        'CENTRAL_HEATING', 'WASHING_MACHINE', 'DISHWASHER', 'ELEVATOR', 'FURNISHED')),
    PRIMARY KEY (listing_id, amenity)
);

INSERT INTO listing_amenities (listing_id, amenity) SELECT id, 'PARKING' FROM listings WHERE has_parking;
ALTER TABLE listings DROP COLUMN has_parking;
