package com.sanele.bookings.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * We store only the remote event ID — no cross-service join.
     * This is intentional microservices design: services are decoupled.
     */
    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 150)
    private String customerName;

    @Column(nullable = false, length = 254)
    private String customerEmail;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime bookedAt;

    @Column
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        this.bookedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = BookingStatus.PENDING;
        }
    }
}
