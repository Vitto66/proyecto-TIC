package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Aviso para un turista que ve al entrar a su espacio. Lo crea el sistema, por ejemplo cuando
 * una reserva vence (CU-16 paso 4). Queda sin leer hasta que el turista lo marca como visto.
 */
@Entity
@Table(name = "tourist_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TouristNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** tourist_id referencia a tourist(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tourist_id", nullable = false)
    private Tourist tourist;

    /** La reserva de la que habla el aviso, para enlazarla. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Cuándo lo vio el turista. null = sin leer. */
    @Column(name = "read_at")
    private LocalDateTime readAt;
}
