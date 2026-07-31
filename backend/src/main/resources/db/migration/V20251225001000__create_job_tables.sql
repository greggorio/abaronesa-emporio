-- Tabela de definições de jobs agendados
CREATE TABLE job_definition (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    cron VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    description VARCHAR(500),
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP
);

-- Tabela de execuções de jobs
CREATE TABLE job_execution (
    id BIGSERIAL PRIMARY KEY,
    job_definition_id BIGINT NOT NULL REFERENCES job_definition(id) ON DELETE CASCADE,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    records_affected INTEGER,
    message TEXT,
    payload_log TEXT
);

CREATE INDEX idx_job_execution_job ON job_execution (job_definition_id);
CREATE INDEX idx_job_execution_started ON job_execution (started_at DESC);
