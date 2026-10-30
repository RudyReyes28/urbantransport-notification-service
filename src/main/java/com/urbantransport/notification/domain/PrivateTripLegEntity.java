package com.urbantransport.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "private_trip_leg")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivateTripLegEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rental_id", nullable = false)
    private UUID rentalId;

    @Column(name = "leg_no", nullable = false)
    private int legNo;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "customer_nit", length = 20)
    private String customerNit;

    @Column(name = "bus_id", nullable = false)
    private UUID busId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "origin_text", nullable = false, length = 200)
    private String originText;

    @Column(name = "destination_text", nullable = false, length = 200)
    private String destinationText;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "estimated_arrival", nullable = false)
    private LocalDateTime estimatedArrival;

    /** Viaje de trip que ya se unió a este tramo. */
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "notified_at")
    private Instant notifiedAt;
}