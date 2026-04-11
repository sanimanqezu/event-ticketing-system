package com.sanele.bookings.repository;

import com.sanele.bookings.entity.Booking;
import com.sanele.bookings.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByEventId(Long eventId);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByEventIdAndStatus(Long eventId, BookingStatus status);

    List<Booking> findByCustomerEmailIgnoreCase(String customerEmail);
}
