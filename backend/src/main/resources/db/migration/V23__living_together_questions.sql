-- Questions about living together. Cleaning replaces the 1-5 tidiness score and sound replaces the
-- 1-5 need for quiet; the other four are new.
ALTER TABLE profiles
    ADD COLUMN cleaning      VARCHAR(20),
    ADD COLUMN sound         VARCHAR(20),
    ADD COLUMN communication VARCHAR(20),
    ADD COLUMN sharing       VARCHAR(20),
    ADD COLUMN parties       VARCHAR(20),
    ADD COLUMN partner_stays VARCHAR(20);

-- Existing profiles keep what their old answers meant, and get the middle answer for the new questions
-- until the student edits the profile.
UPDATE profiles SET
    cleaning = CASE WHEN tidiness >= 4 THEN 'REGULAR' WHEN tidiness = 3 THEN 'SCHEDULE' ELSE 'WHEN_POSSIBLE' END,
    sound = CASE WHEN quiet_need >= 4 THEN 'HEADPHONES' WHEN quiet_need = 3 THEN 'QUIET_SPEAKER' ELSE 'LOUD' END,
    communication = 'OCCASIONAL',
    sharing = 'BASICS',
    parties = 'SMALL',
    partner_stays = 'SOMETIMES';

ALTER TABLE profiles
    ALTER COLUMN cleaning SET NOT NULL,
    ALTER COLUMN sound SET NOT NULL,
    ALTER COLUMN communication SET NOT NULL,
    ALTER COLUMN sharing SET NOT NULL,
    ALTER COLUMN parties SET NOT NULL,
    ALTER COLUMN partner_stays SET NOT NULL,
    ADD CONSTRAINT profiles_cleaning_check CHECK (cleaning IN ('WHEN_POSSIBLE', 'SCHEDULE', 'REGULAR')),
    ADD CONSTRAINT profiles_sound_check CHECK (sound IN ('HEADPHONES', 'QUIET_SPEAKER', 'LOUD')),
    ADD CONSTRAINT profiles_communication_check CHECK (communication IN ('MINIMAL', 'OCCASIONAL', 'TOGETHER')),
    ADD CONSTRAINT profiles_sharing_check CHECK (sharing IN ('SEPARATE', 'BASICS', 'EVERYTHING')),
    ADD CONSTRAINT profiles_parties_check CHECK (parties IN ('NONE', 'SMALL', 'PARTIES')),
    ADD CONSTRAINT profiles_partner_stays_check CHECK (partner_stays IN ('NO', 'SOMETIMES', 'YES'));

ALTER TABLE profiles DROP COLUMN tidiness, DROP COLUMN quiet_need;
