-- Users with a role, and an append-only audit log of every change.

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('OWNER', 'VIEWER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id          BIGSERIAL     PRIMARY KEY,
    occurred_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    username    VARCHAR(100)  NOT NULL,
    method      VARCHAR(10)   NOT NULL,
    path        VARCHAR(500)  NOT NULL,
    status      INT           NOT NULL
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);

-- The log can only grow: nobody, not even the application, can change or erase a line.
CREATE FUNCTION forbid_audit_log_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_log_change();
