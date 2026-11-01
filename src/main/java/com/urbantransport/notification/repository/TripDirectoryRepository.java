package com.urbantransport.notification.repository;

import com.urbantransport.notification.domain.TripDirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TripDirectoryRepository extends JpaRepository<TripDirectoryEntity, UUID> {

    /** Viajes privados que todavía no se unieron a ningún tramo de alquiler. */
    @Query("""
            select t from TripDirectoryEntity t
            where t.type = com.urbantransport.notification.domain.TripKind.PRIVATE
              and t.rentalId is null
              and t.busId = :busId and t.driverId = :driverId and t.departureAt = :departureAt
            """)
    List<TripDirectoryEntity> findUnlinkedPrivate(@Param("busId") UUID busId, @Param("driverId") UUID driverId,
                                                  @Param("departureAt") LocalDateTime departureAt);
}