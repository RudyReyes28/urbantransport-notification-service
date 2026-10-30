package com.urbantransport.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Copia local del personal (alimentada por los eventos de identity) para saber a quién avisar por sucursal. */
@Entity
@Table(name = "user_directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDirectoryEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "branch_id")
    private UUID branchId;

    /** Solo para clientes: id del cliente en customer-service. */
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false)
    private boolean active;

    /** Momento del último evento aplicado: un evento más viejo o igual se descarta. */
    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;
}