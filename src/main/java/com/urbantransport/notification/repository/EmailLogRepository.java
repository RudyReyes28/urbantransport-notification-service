package com.urbantransport.notification.repository;

import com.urbantransport.notification.domain.EmailLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLogEntity, UUID> {

    /** Correos pendientes que ya les toca enviarse, los más antiguos primero. */
    @Query("""
            select e.id from EmailLogEntity e
            where e.status = com.urbantransport.notification.domain.EmailStatus.PENDING
              and e.nextAttemptAt <= :now
            order by e.nextAttemptAt asc
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);

    /**
     * Reclama el correo: solo uno gana, aunque haya varias instancias. Devuelve 1 si lo reclamó.
     * El nuevo next_attempt_at es el "arrendamiento": si el proceso se cae, el correo se reintenta al vencer.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailLogEntity e set e.nextAttemptAt = :lease
            where e.id = :id
              and e.status = com.urbantransport.notification.domain.EmailStatus.PENDING
              and e.nextAttemptAt <= :now
            """)
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("lease") Instant lease);

    /** ¿Ya existe este correo para este evento, plantilla y destinatario ('' si no hay)? */
    @Query("""
            select count(e) > 0 from EmailLogEntity e
            where e.eventId = :eventId and e.template = :template and coalesce(e.recipient, '') = :recipient
            """)
    boolean existsDuplicate(@Param("eventId") UUID eventId, @Param("template") String template,
                            @Param("recipient") String recipient);
}