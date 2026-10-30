-- Último estado conocido de cada viaje: sirve para decir qué cambió y para completar TripDeleted
CREATE TABLE trip_directory (
                                trip_id           UUID         PRIMARY KEY,
                                type              VARCHAR(10)  NOT NULL,
                                origin_name       VARCHAR(200),
                                destination_name  VARCHAR(200),
                                departure_at      TIMESTAMP    NOT NULL,
                                estimated_arrival TIMESTAMP    NOT NULL,
                                bus_id            UUID,
                                bus_plate         VARCHAR(20),
                                driver_id         UUID,
                                status            VARCHAR(20),
                                rental_id         UUID,
                                last_event_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_trip_directory_private_match ON trip_directory (bus_id, driver_id, departure_at)
    WHERE type = 'PRIVATE' AND rental_id IS NULL;