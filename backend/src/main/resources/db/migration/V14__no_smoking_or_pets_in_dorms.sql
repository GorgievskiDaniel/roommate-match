-- Dorms allow neither smoking nor pets, so these answers no longer apply to dorm profiles.
UPDATE profiles
SET smoker = FALSE, has_pets = FALSE, accepts_smoker = FALSE, accepts_pets = FALSE
WHERE housing_type = 'DORM';
