CREATE TABLE IF NOT EXISTS entity_translation (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    field VARCHAR(64) NOT NULL,
    locale VARCHAR(16) NOT NULL,
    source_text TEXT,
    source_hash VARCHAR(128),
    translated_text TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'OK',
    provider VARCHAR(32),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_entity_translation_key
    ON entity_translation (entity_type, entity_id, field, locale);

CREATE INDEX IF NOT EXISTS idx_entity_translation_status
    ON entity_translation (status);
