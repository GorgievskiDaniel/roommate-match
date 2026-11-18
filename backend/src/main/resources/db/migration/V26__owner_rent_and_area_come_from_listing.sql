-- A student who has a place does not give a budget or areas: the rent and the location come from the listing.
ALTER TABLE profiles DROP CONSTRAINT housing_fields_match_type;

UPDATE profiles SET budget_min = NULL, budget_max = NULL WHERE situation = 'HAS_PLACE';
DELETE FROM profile_areas WHERE user_id IN (SELECT user_id FROM profiles WHERE situation = 'HAS_PLACE');

ALTER TABLE profiles ADD CONSTRAINT housing_fields_match_type CHECK (
    (housing_type = 'DORM' AND dorm_id IS NOT NULL AND situation IS NULL
        AND budget_min IS NULL AND budget_max IS NULL)
    OR
    (housing_type = 'APARTMENT' AND dorm_id IS NULL AND situation = 'HAS_PLACE'
        AND budget_min IS NULL AND budget_max IS NULL)
    OR
    (housing_type = 'APARTMENT' AND dorm_id IS NULL AND situation IS NOT NULL AND situation <> 'HAS_PLACE'
        AND budget_min IS NOT NULL AND budget_max IS NOT NULL AND budget_min <= budget_max)
);
