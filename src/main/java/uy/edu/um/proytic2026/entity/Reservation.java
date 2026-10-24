package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reserva de un turista para una instancia de actividad. La crea el CU-14 y la confirma el
 * pago (CU-15). Por ahora se usa para contar los cupos ocupados de cada instancia (CU-09,
 * CU-06): solo las CONFIRMED y FINISHED ocupan lugar.
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
}
