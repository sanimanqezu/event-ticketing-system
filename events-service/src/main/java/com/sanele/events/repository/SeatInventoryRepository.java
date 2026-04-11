package com.sanele.events.repository;

import com.sanele.events.entity.SeatInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatInventoryRepository extends JpaRepository<SeatInventory, Long> {

    Optional<SeatInventory> findByEventId(Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatInventory s WHERE s.event.id = :eventId")
    Optional<SeatInventory> findByEventIdWithLock(@Param("eventId") Long eventId);
}
