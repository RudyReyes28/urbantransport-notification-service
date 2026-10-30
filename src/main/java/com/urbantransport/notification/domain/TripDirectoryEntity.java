package com.urbantransport.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Último estado conocido de un viaje. Las fechas están en hora de Guatemala y truncadas al minuto. */
@Entity
@Table(name = "trip_directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripDirectoryEntity {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripKind type;

    @Column(name = "origin_name", length = 200)
    private String originName;

    @Column(name = "destination_name", length = 200)
    private String destinationName;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "estimated_arrival", nullable = false)
    private LocalDateTime estimatedArrival;

    @Column(name = "bus_id")
    private UUID busId;

    @Column(name = "bus_plate", length = 20)
    private String busPlate;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(length = 20)
    private String status;

    /** Solo viajes privados: alquiler al que pertenece, una vez cruzado con su asignación. */
    @Column(name = "rental_id")
    private UUID rentalId;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;
}