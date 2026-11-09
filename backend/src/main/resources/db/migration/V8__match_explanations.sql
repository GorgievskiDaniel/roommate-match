-- The wording addresses the reader, so a pair has one row per direction.
-- input_hash tells whether the facts behind the text are still the current ones.
CREATE TABLE match_explanations (
    id         BIGSERIAL PRIMARY KEY,
    viewer_id  BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    other_id   BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    input_hash VARCHAR(64)   NOT NULL,
    text       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (viewer_id, other_id)
);
