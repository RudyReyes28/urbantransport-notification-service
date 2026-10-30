-- Quién compró boletos de cada viaje (de TicketPurchased), para avisarle si el viaje cambia
CREATE TABLE trip_passenger (
                                ticket_id    UUID         PRIMARY KEY,
                                trip_id      UUID         NOT NULL,
                                order_id     UUID         NOT NULL,
                                email        VARCHAR(150) NOT NULL,
                                customer_nit VARCHAR(20),
                                seat_label   VARCHAR(10)  NOT NULL
);

CREATE INDEX ix_trip_passenger_trip ON trip_passenger (trip_id);