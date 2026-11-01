package com.urbantransport.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "trip_passenger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripPassengerEntity {

    @Id
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "customer_nit", length = 20)
    private String customerNit;

    @Column(name = "seat_label", nullable = false, length = 10)
    private String seatLabel;
}