CREATE TABLE dorm_room_options (
    id      BIGSERIAL PRIMARY KEY,
    dorm_id BIGINT       NOT NULL REFERENCES dorms (id),
    name    VARCHAR(100) NOT NULL,
    UNIQUE (dorm_id, name)
);

INSERT INTO dorm_room_options (dorm_id, name)
SELECT d.id, o.name
FROM dorms d
JOIN (VALUES
    ('ДСД Гоце Делчев', 'Со кујна (блокови А и Б)', 1),
    ('ДСД Гоце Делчев', 'Со тоалет (блокови В и Г)', 2),
    ('Стив Наумов', 'Згради', 3),
    ('Стив Наумов', 'Бараки', 4)
) AS o (dorm_name, name, position) ON o.dorm_name = d.name
ORDER BY o.position;

ALTER TABLE profiles
    ADD COLUMN dorm_room_option_id BIGINT REFERENCES dorm_room_options (id),
    ADD CONSTRAINT room_option_only_for_dorm CHECK (dorm_room_option_id IS NULL OR housing_type = 'DORM');

-- OpenStreetMap: "Студентски дом Стив Наумов", Гази Баба, Скопје.
UPDATE dorms SET latitude = 42.001238, longitude = 21.455575 WHERE name = 'Стив Наумов';

CREATE TABLE listings (
    id            BIGSERIAL PRIMARY KEY,
    owner_id      BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    property_type VARCHAR(10)      NOT NULL CHECK (property_type IN ('APARTMENT', 'HOUSE')),
    address       VARCHAR(200)     NOT NULL,
    area          VARCHAR(20)      NOT NULL,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    monthly_rent  INTEGER          NOT NULL CHECK (monthly_rent >= 0),
    free_spots    SMALLINT         NOT NULL CHECK (free_spots BETWEEN 1 AND 10),
    description   VARCHAR(1000)    NOT NULL,
    invite_code   VARCHAR(12)      NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- A person lives in one place at a time. The owner has a row here too.
CREATE TABLE listing_residents (
    listing_id BIGINT NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (listing_id, user_id)
);

CREATE TABLE listing_images (
    id         BIGSERIAL PRIMARY KEY,
    listing_id BIGINT       NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    file_name  VARCHAR(60)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_listing_images_listing ON listing_images (listing_id);
