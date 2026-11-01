package com.urbantransport.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Copia local de los choferes (alimentada por fleet-events): contacto, sucursal y vencimiento de licencia. */
@Entity
@Table(name = "driver_directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverDirectoryEntity {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "license_number", nullable = false, length = 30)
    private String licenseNumber;

    @Column(name = "license_expires_on", nullable = false)
    private LocalDate licenseExpiresOn;

    @Column(nullable = false)
    private boolean active;

    /** Momento del último evento aplicado: un evento más viejo o igual se descarta. */
    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;
}