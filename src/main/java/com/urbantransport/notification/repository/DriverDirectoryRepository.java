package com.urbantransport.notification.repository;

import com.urbantransport.notification.domain.DriverDirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DriverDirectoryRepository extends JpaRepository<DriverDirectoryEntity, UUID> {

    /** Choferes activos cuya licencia vence entre las dos fechas (inclusive). */
    List<DriverDirectoryEntity> findByActiveTrueAndLicenseExpiresOnBetween(LocalDate from, LocalDate to);

    /**
     * Reclama el aviso de un umbral: devuelve 1 si lo reclamó y 0 si ya estaba enviado. Solo uno gana aunque
     * haya varias instancias. Debe llamarse dentro de una transacción.
     */
    @Modifying
    @Query(value = """
            INSERT INTO license_alert_sent (driver_id, expires_on, days_before, sent_at)
            VALUES (:driverId, :expiresOn, :daysBefore, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int claimAlert(@Param("driverId") UUID driverId, @Param("expiresOn") LocalDate expiresOn,
                   @Param("daysBefore") int daysBefore);
}