-- A student without a place may look both for someone who has one and for someone to search with.
ALTER TABLE profiles DROP CONSTRAINT profiles_situation_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_situation_check
    CHECK (situation IN ('HAS_PLACE', 'NEEDS_PLACE', 'SEARCH_TOGETHER', 'NEEDS_OR_TOGETHER'));
