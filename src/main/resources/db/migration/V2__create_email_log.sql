CREATE TABLE email_log (
                           id              UUID         PRIMARY KEY,
                           event_id        UUID         NOT NULL,
                           event_type      VARCHAR(60)  NOT NULL,
                           reference_id    UUID,
                           recipient       VARCHAR(150),
                           template        VARCHAR(60)  NOT NULL,
                           subject         VARCHAR(200) NOT NULL,
                           body            TEXT,
                           status          VARCHAR(10)  NOT NULL,
                           attempts        INTEGER      NOT NULL DEFAULT 0,
                           last_error      TEXT,
                           next_attempt_at TIMESTAMPTZ,
                           created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                           sent_at         TIMESTAMPTZ,
                           CONSTRAINT ck_email_log_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
                           CONSTRAINT ck_email_log_attempts CHECK (attempts >= 0)
);

-- Un mismo evento no genera dos veces el mismo correo para el mismo destinatario
CREATE UNIQUE INDEX uq_email_log_dedupe ON email_log (event_id, template, COALESCE(recipient, ''));

CREATE INDEX ix_email_log_pending ON email_log (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_email_log_created ON email_log (created_at DESC);
CREATE INDEX ix_email_log_reference ON email_log (reference_id);