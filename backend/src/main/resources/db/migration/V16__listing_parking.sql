-- Whether the flat or house comes with a parking place. Listings made before this have none recorded.
ALTER TABLE listings ADD COLUMN has_parking BOOLEAN NOT NULL DEFAULT FALSE;
