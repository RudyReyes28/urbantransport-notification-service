-- Tramos de cada asignación de alquiler (de RentalAssigned), para unirlos con el viaje que programa trip
CREATE TABLE private_trip_leg (
                                  id                UUID         PRIMARY KEY,
                                  rental_id         UUID         NOT NULL,
                                  leg_no            INTEGER      NOT NULL,
                                  branch_id         UUID         NOT NULL,
                                  customer_user_id  UUID,
                                  customer_nit      VARCHAR(20),
                                  bus_id            UUID         NOT NULL,
                                  driver_id         UUID         NOT NULL,
                                  origin_text       VARCHAR(200) NOT NULL,
                                  destination_text  VARCHAR(200) NOT NULL,
                                  departure_at      TIMESTAMP    NOT NULL,
                                  estimated_arrival TIMESTAMP    NOT NULL,
                                  trip_id           UUID,
                                  notified_at       TIMESTAMPTZ,
                                  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_private_trip_leg_rental ON private_trip_leg (rental_id);
CREATE INDEX ix_private_trip_leg_match ON private_trip_leg (bus_id, driver_id, departure_at) WHERE trip_id IS NULL;