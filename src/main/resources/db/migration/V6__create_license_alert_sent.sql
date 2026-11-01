-- Qué umbral de aviso ya se envió para cada licencia. Si el chofer renueva (nueva fecha), los avisos empiezan de nuevo.
CREATE TABLE license_alert_sent (
                                    driver_id   UUID        NOT NULL,
                                    expires_on  DATE        NOT NULL,
                                    days_before INTEGER     NOT NULL,
                                    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    PRIMARY KEY (driver_id, expires_on, days_before)
);