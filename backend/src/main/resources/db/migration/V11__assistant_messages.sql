-- The assistant's conversation with each student, so it can follow up on earlier questions.
CREATE TABLE assistant_messages (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role          VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content       TEXT        NOT NULL,
    -- JSON arrays of document names.
    sources       TEXT        NOT NULL DEFAULT '[]',
    attachments   TEXT        NOT NULL DEFAULT '[]',
    offered_files TEXT        NOT NULL DEFAULT '[]',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_assistant_messages_user ON assistant_messages (user_id, id);
