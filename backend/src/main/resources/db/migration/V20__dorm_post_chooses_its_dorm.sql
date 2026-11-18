-- Anyone may post a dorm ad now, not only students whose profile is set to a dorm, so the ad names
-- its own dorm. Existing ads take the dorm from the author's profile.
ALTER TABLE dorm_posts ADD COLUMN dorm_id BIGINT REFERENCES dorms (id);
UPDATE dorm_posts p SET dorm_id = pr.dorm_id FROM profiles pr WHERE pr.user_id = p.user_id;
DELETE FROM dorm_posts WHERE dorm_id IS NULL;
ALTER TABLE dorm_posts ALTER COLUMN dorm_id SET NOT NULL;
CREATE INDEX idx_dorm_posts_dorm ON dorm_posts (dorm_id);
