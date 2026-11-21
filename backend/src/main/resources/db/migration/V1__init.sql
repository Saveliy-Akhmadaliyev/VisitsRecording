-- V1: начальная схема БД.
-- Источник: «Сущности для бека.md».
-- Отклонение от источника: добавлены updated_at в locations / templates /
-- template_fields — нужны для delta-синхронизации с мобильным приложением.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    full_name     VARCHAR(255) NOT NULL,
    login         VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'INSPECTOR')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_users_login UNIQUE (login)
);

CREATE TABLE locations (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    address    TEXT         NOT NULL,
    target_lat NUMERIC(9, 6),
    target_lon NUMERIC(9, 6),
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE templates (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    version     INTEGER      NOT NULL,
    is_archived BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE template_fields (
    id                     UUID PRIMARY KEY,
    template_id            UUID          NOT NULL REFERENCES templates (id),
    order_index            INTEGER       NOT NULL,
    title                  VARCHAR(1024) NOT NULL,
    field_type             VARCHAR(16)   NOT NULL CHECK (field_type IN ('TEXT', 'RATING_1_5', 'YES_NO', 'SELECT')),
    options                JSONB,
    is_required            BOOLEAN       NOT NULL DEFAULT FALSE,
    require_photo_on_issue BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at             TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_template_fields_template ON template_fields (template_id);

CREATE TABLE visits (
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    template_id  UUID NOT NULL REFERENCES templates (id),
    location_id  UUID NOT NULL REFERENCES locations (id),
    admin_id     UUID NOT NULL REFERENCES users (id),
    inspector_id UUID NOT NULL REFERENCES users (id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    fact_lat     NUMERIC(9, 6),
    fact_lon     NUMERIC(9, 6),
    status       VARCHAR(16) NOT NULL CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'OVERDUE')),
    updated_at   TIMESTAMPTZ NOT NULL
);
-- Главный запрос синхронизации: «визиты инспектора, изменённые после X»
CREATE INDEX idx_visits_inspector_updated ON visits (inspector_id, updated_at);
CREATE INDEX idx_visits_status ON visits (status);

CREATE TABLE visit_answers (
    id        UUID PRIMARY KEY,  -- генерируется на телефоне
    visit_id  UUID  NOT NULL REFERENCES visits (id),
    field_id  UUID  NOT NULL REFERENCES template_fields (id),
    value     TEXT  NOT NULL,
    has_issue BOOLEAN NOT NULL DEFAULT FALSE,
    comment   TEXT
);
CREATE INDEX idx_visit_answers_visit ON visit_answers (visit_id);
CREATE INDEX idx_visit_answers_issue ON visit_answers (has_issue) WHERE has_issue;

CREATE TABLE media_files (
    id         UUID PRIMARY KEY,  -- генерируется на телефоне
    visit_id   UUID NOT NULL REFERENCES visits (id),
    answer_id  UUID REFERENCES visit_answers (id),
    s3_key     VARCHAR(512) NOT NULL,
    file_type  VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_media_files_visit ON media_files (visit_id);
