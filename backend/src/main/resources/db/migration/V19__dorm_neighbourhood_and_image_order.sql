-- The part of town people know a dorm by, when it differs from the municipality. Стив Наумов is in
-- Автокоманда, which belongs to the municipality of Гази Баба.
ALTER TABLE dorms ADD COLUMN neighbourhood VARCHAR(60);
UPDATE dorms SET neighbourhood = 'Автокоманда' WHERE slug = 'stiv-naumov';

-- The order of a listing's photos. The first one is the main photo on cards and the map.
ALTER TABLE listing_images ADD COLUMN position INTEGER NOT NULL DEFAULT 0;
UPDATE listing_images i SET position = ranked.n
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY listing_id ORDER BY id) - 1 AS n FROM listing_images) ranked
WHERE ranked.id = i.id;
