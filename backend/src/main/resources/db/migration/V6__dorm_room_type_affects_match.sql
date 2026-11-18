-- In some dorms the room type changes daily life enough to weigh heavily on a match.
ALTER TABLE dorms ADD COLUMN room_type_affects_match BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE dorms SET room_type_affects_match = TRUE WHERE name = 'ДСД Гоце Делчев';
