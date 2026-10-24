package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Valoración de un turista sobre una reserva finalizada (CU-18): puntaje de 1 a 5 y un
 * comentario opcional. Una sola por reserva: la base tiene UNIQUE (reservation_id).
 * La calificación de una actividad es el promedio de las valoraciones de sus reservas (CU-05).
 */
@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    /** De 1 a 5; la base lo controla con un CHECK. */
    @Column(nullable = false)
    private Integer rating;

    /** Opcional. En Supabase la columna es TEXT. */
    @Column(length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
