CREATE TABLE profile_property_types (
    user_id       BIGINT      NOT NULL REFERENCES profiles (user_id) ON DELETE CASCADE,
    property_type VARCHAR(10) NOT NULL CHECK (property_type IN ('APARTMENT', 'HOUSE')),
    PRIMARY KEY (user_id, property_type)
);

-- Until now the questionnaire only offered "apartment or house", so existing answers mean both.
INSERT INTO profile_property_types (user_id, property_type)
SELECT p.user_id, t.property_type
FROM profiles p
CROSS JOIN (VALUES ('APARTMENT'), ('HOUSE')) AS t (property_type)
WHERE p.housing_type = 'APARTMENT';
