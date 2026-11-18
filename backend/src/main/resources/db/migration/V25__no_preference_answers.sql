-- "Не ми е важно" for sound, communication and sharing: the question then does not count for that pair.
ALTER TABLE profiles DROP CONSTRAINT profiles_sound_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_sound_check
    CHECK (sound IN ('HEADPHONES', 'QUIET_SPEAKER', 'LOUD', 'NO_PREFERENCE'));
ALTER TABLE profiles DROP CONSTRAINT profiles_communication_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_communication_check
    CHECK (communication IN ('MINIMAL', 'OCCASIONAL', 'TOGETHER', 'NO_PREFERENCE'));
ALTER TABLE profiles DROP CONSTRAINT profiles_sharing_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_sharing_check
    CHECK (sharing IN ('SEPARATE', 'BASICS', 'EVERYTHING', 'NO_PREFERENCE'));
