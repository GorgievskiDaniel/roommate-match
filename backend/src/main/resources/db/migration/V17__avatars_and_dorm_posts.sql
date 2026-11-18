-- The stored file name of the student's own picture. Without one, a drawing by gender is shown.
ALTER TABLE profiles ADD COLUMN avatar VARCHAR(60);

-- A dorm student's short ad that they are looking for a roommate. The dorm is the one on the profile,
-- so it is not stored twice. One ad per student.
CREATE TABLE dorm_posts (
    user_id    BIGINT        PRIMARY KEY REFERENCES profiles (user_id) ON DELETE CASCADE,
    text       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
