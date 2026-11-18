-- A dorm spread over several buildings gets one map point per block.
CREATE TABLE dorm_blocks (
    id             BIGSERIAL PRIMARY KEY,
    dorm_id        BIGINT           NOT NULL REFERENCES dorms (id),
    name           VARCHAR(40)      NOT NULL,
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    room_option_id BIGINT           REFERENCES dorm_room_options (id),
    UNIQUE (dorm_id, name)
);

INSERT INTO dorm_blocks (dorm_id, name, latitude, longitude, room_option_id)
SELECT d.id, b.name, b.latitude, b.longitude, o.id
FROM dorms d
JOIN (VALUES
    ('Блок А', 41.99903870, 21.38957024, 'Со кујна (блокови А и Б)', 1),
    ('Блок Б', 41.99895859, 21.39040375, 'Со кујна (блокови А и Б)', 2),
    ('Блок В', 41.99938965, 21.39038849, 'Со тоалет (блокови В и Г)', 3),
    ('Блок Г', 41.99958802, 21.38982773, 'Со тоалет (блокови В и Г)', 4)
) AS b (name, latitude, longitude, option_name, position) ON TRUE
JOIN dorm_room_options o ON o.dorm_id = d.id AND o.name = b.option_name
WHERE d.name = 'ДСД Гоце Делчев'
ORDER BY b.position;
