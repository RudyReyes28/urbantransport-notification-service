package com.urbantransport.notification.repository;

import com.urbantransport.notification.domain.PrivateTripLegEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PrivateTripLegRepository extends JpaRepository<PrivateTripLegEntity, UUID> {

    List<PrivateTripLegEntity> findByRentalIdOrderByLegNoAsc(UUID rentalId);

    /** Tramos que todavía no se unieron a ningún viaje de trip. */
    @Query("""
            select l from PrivateTripLegEntity l
            where l.tripId is null
              and l.busId = :busId and l.driverId = :driverId and l.departureAt = :departureAt
            """)
    List<PrivateTripLegEntity> findUnlinked(@Param("busId") UUID busId, @Param("driverId") UUID driverId,
                                            @Param("departureAt") LocalDateTime departureAt);

    /** Una reasignación (o un rechazo) descarta los tramos del intento anterior que nunca se programaron. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PrivateTripLegEntity l where l.rentalId = :rentalId and l.tripId is null")
    int deleteUnlinkedByRentalId(@Param("rentalId") UUID rentalId);
}