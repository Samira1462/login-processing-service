CREATE SCHEMA IF NOT EXISTS login_processing;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS login_processing.login_tracking_result (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id        UUID NOT NULL,
    customer_id       UUID NOT NULL,
    username          VARCHAR(150) NOT NULL,
    client            VARCHAR(16) NOT NULL, -- WEB/ANDROID/IOS
    event_timestamp   TIMESTAMPTZ NOT NULL,
    customer_ip       VARCHAR(45) NOT NULL, -- IPv4/IPv6
    request_result    VARCHAR(16) NOT NULL, -- SUCCESSFUL/UNSUCCESSFUL
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_login_tracking_result_message_id UNIQUE (message_id),

    CONSTRAINT chk_login_tracking_result_client
    CHECK (client IN ('WEB', 'ANDROID', 'IOS')),

    CONSTRAINT chk_login_tracking_result_request_result
    CHECK (request_result IN ('SUCCESSFUL', 'UNSUCCESSFUL'))
    );

CREATE INDEX IF NOT EXISTS ix_login_tracking_result_customer_id
    ON login_processing.login_tracking_result (customer_id);

CREATE INDEX IF NOT EXISTS ix_login_tracking_result_event_timestamp
    ON login_processing.login_tracking_result (event_timestamp);

CREATE TABLE IF NOT EXISTS login_processing.outbox_event (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    aggregate_type   VARCHAR(64) NOT NULL,
    aggregate_id     UUID NOT NULL,
    event_type       VARCHAR(64) NOT NULL,

    topic            TEXT NOT NULL,
    key              TEXT NOT NULL,
    payload          BYTEA NOT NULL,

    status           VARCHAR(16) NOT NULL,
    retry_count      INT NOT NULL DEFAULT 0,
    last_error       TEXT NULL,

    version          BIGINT NOT NULL DEFAULT 0,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempt_at  TIMESTAMPTZ NULL,
    sent_at          TIMESTAMPTZ NULL,

    CONSTRAINT ux_outbox_event_aggregate_event
    UNIQUE (aggregate_type, aggregate_id, event_type),

    CONSTRAINT chk_outbox_event_status
    CHECK (status IN ('NEW', 'SENT', 'FAILED')),

    CONSTRAINT chk_outbox_event_aggregate_type
    CHECK (aggregate_type IN ('LOGIN_TRACKING_RESULT')),

    CONSTRAINT chk_outbox_event_event_type
    CHECK (event_type IN ('LOGIN_TRACKING_RESULT_CREATED'))
    );

CREATE INDEX IF NOT EXISTS ix_outbox_event_status_created_at
    ON login_processing.outbox_event (status, created_at);

CREATE INDEX IF NOT EXISTS ix_outbox_event_aggregate_id
    ON login_processing.outbox_event (aggregate_id);

CREATE INDEX IF NOT EXISTS ix_outbox_event_created_at
    ON login_processing.outbox_event (created_at);

