package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reserva de un turista para una instancia de actividad. La crea el CU-14 (con los datos de
 * cada acompañante) y la confirma el pago (CU-15). Solo las CONFIRMED y FINISHED ocupan lugar
 * (CU-09, CU-06).
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** tourist_id referencia a tourist(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tourist_id", nullable = false)
    private Tourist tourist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false)
    private ActivityInstance instance;

    @Column(nullable = false)
    private Integer participants;

    /** Precio total al momento de reservar: un cambio de precio posterior no lo afecta (CU-10). */
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Las personas 2 en adelante (la 1 es el turista), con sus datos. */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    @Builder.Default
    private List<ReservationParticipant> companions = new ArrayList<>();

    /** Agrega un acompañante con el número de persona que le sigue. */
    public void addCompanion(ReservationParticipant companion) {
        companion.setReservation(this);
        companion.setPosition(companions.size() + 2);
        companions.add(companion);
    }
}
