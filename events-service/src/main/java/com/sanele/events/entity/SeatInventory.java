package com.sanele.events.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seat_inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(nullable = false)
    private int totalSeats;

    @Column(nullable = false)
    private int reservedSeats;

    @Column(nullable = false)
    private int availableSeats;

    @Version
    private Long version;
}
