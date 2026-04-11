package com.sanele.events.repository;

import com.sanele.events.entity.Event;
import com.sanele.events.entity.EventCategory;
import com.sanele.events.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByCategory(EventCategory category);

    List<Event> findByStatus(EventStatus status);

    List<Event> findByCategoryAndStatus(EventCategory category, EventStatus status);
}
