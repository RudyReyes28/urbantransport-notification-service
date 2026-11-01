package com.urbantransport.notification.repository;

import com.urbantransport.notification.domain.TripPassengerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripPassengerRepository extends JpaRepository<TripPassengerEntity, UUID> {

    List<TripPassengerEntity> findByTripId(UUID tripId);
}