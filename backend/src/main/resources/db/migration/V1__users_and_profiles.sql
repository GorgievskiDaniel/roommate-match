CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Coordinates are filled in during phase 2, from verified sources.
CREATE TABLE dorms (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(100) NOT NULL UNIQUE,
    latitude  DOUBLE PRECISION,
    longitude DOUBLE PRECISION
);

INSERT INTO dorms (name) VALUES
    ('ДСД Гоце Делчев'),
    ('Стив Наумов'),
    ('ДСД Кочо Рацин'),
    ('ДСД Никола Карев'),
    ('ДСД Браќа Миладиновци'),
    ('ДСД Тетово'),
    ('ПСД Хилјадников'),
    ('ПСД Логос');

CREATE TABLE hobbies (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE
);

INSERT INTO hobbies (name) VALUES
    ('Спорт'), ('Фитнес'), ('Планинарење'), ('Музика'), ('Свирење инструмент'),
    ('Филмови и серии'), ('Видео игри'), ('Читање'), ('Готвење'), ('Патување'),
    ('Фотографија'), ('Цртање и уметност'), ('Програмирање'), ('Танц'),
    ('Излегување'), ('Друштвени игри');

CREATE TABLE profiles (
    user_id                    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    first_name                 VARCHAR(60)  NOT NULL,
    gender                     VARCHAR(10)  NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    birth_date                 DATE         NOT NULL,
    faculty                    VARCHAR(120) NOT NULL,
    bio                        VARCHAR(500) NOT NULL DEFAULT '',
    housing_type               VARCHAR(10)  NOT NULL CHECK (housing_type IN ('APARTMENT', 'DORM')),
    situation                  VARCHAR(20)  CHECK (situation IN ('HAS_PLACE', 'NEEDS_PLACE', 'SEARCH_TOGETHER')),
    dorm_id                    BIGINT REFERENCES dorms (id),
    budget_min                 INTEGER CHECK (budget_min >= 0),
    budget_max                 INTEGER,
    smoker                     BOOLEAN      NOT NULL,
    has_pets                   BOOLEAN      NOT NULL,
    guests_frequency           VARCHAR(10)  NOT NULL CHECK (guests_frequency IN ('RARELY', 'SOMETIMES', 'OFTEN')),
    tidiness                   SMALLINT     NOT NULL CHECK (tidiness BETWEEN 1 AND 5),
    sleep_schedule             VARCHAR(10)  NOT NULL CHECK (sleep_schedule IN ('EARLY', 'FLEXIBLE', 'LATE')),
    quiet_need                 SMALLINT     NOT NULL CHECK (quiet_need BETWEEN 1 AND 5),
    accepts_smoker             BOOLEAN      NOT NULL,
    accepts_pets               BOOLEAN      NOT NULL,
    roommate_gender_preference VARCHAR(10)  NOT NULL CHECK (roommate_gender_preference IN ('SAME', 'ANY')),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT housing_fields_match_type CHECK (
        (housing_type = 'DORM' AND dorm_id IS NOT NULL AND situation IS NULL
            AND budget_min IS NULL AND budget_max IS NULL)
        OR
        (housing_type = 'APARTMENT' AND dorm_id IS NULL AND situation IS NOT NULL
            AND budget_min IS NOT NULL AND budget_max IS NOT NULL AND budget_min <= budget_max)
    )
);

CREATE INDEX idx_profiles_dorm ON profiles (dorm_id);

CREATE TABLE profile_areas (
    user_id BIGINT      NOT NULL REFERENCES profiles (user_id) ON DELETE CASCADE,
    area    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, area)
);

CREATE TABLE profile_hobbies (
    user_id  BIGINT NOT NULL REFERENCES profiles (user_id) ON DELETE CASCADE,
    hobby_id BIGINT NOT NULL REFERENCES hobbies (id),
    PRIMARY KEY (user_id, hobby_id)
);
